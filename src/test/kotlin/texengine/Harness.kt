// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import com.google.common.collect.Multimaps
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.util.Base64
import java.util.Optional
import java.util.UUID
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.InactiveProfiler
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import sun.misc.Unsafe
import dev.ashtonite365.apex.texengine.apex.TextureConfig
import dev.ashtonite365.apex.gui.config.ManagedOption

object FixturePacks {
	val root: Path by lazy {
		Path.of(System.getProperty("apex.projectDir") ?: error("apex.projectDir not set"))
			.resolve("src/test/resources/texengine-fixtures")
	}

	fun manager(vararg packs: String): MultiPackResourceManager = managerAt(*packs.map { root.resolve(it) }.toTypedArray())

	fun managerAt(vararg packs: Path): MultiPackResourceManager =
		MultiPackResourceManager(PackType.CLIENT_RESOURCES, packs.map {
			PathPackResources(PackLocationInfo("texengine-fixture/${it.fileName}", Component.literal(it.fileName.toString()), PackSource.BUILT_IN, Optional.empty()), it)
		})
}

object Stacks {

	fun sb(
		item: Item = Items.PAPER,
		id: String? = null,
		name: Component? = null,
		lore: List<Component> = listOf(),
		extra: CompoundTag.() -> Unit = {},
	): ItemStack {
		val tag = CompoundTag()
		if (id != null) tag.putString("id", id)
		tag.extra()
		val stack = ItemStack(item)
		if (!tag.isEmpty) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
		if (name != null) stack.set(DataComponents.CUSTOM_NAME, name)
		if (lore.isNotEmpty()) stack.set(DataComponents.LORE, ItemLore(lore))
		return stack
	}

	fun textureValue(skinUrl: String): String =
		Base64.getEncoder().encodeToString("""{"textures":{"SKIN":{"url":"$skinUrl"}}}""".toByteArray())

	fun profile(textureValue: String?, uuid: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")): ResolvableProfile {
		val props = if (textureValue == null) mapOf() else mapOf("textures" to Property("textures", textureValue))
		return ResolvableProfile.createResolved(GameProfile(uuid, "GoldenHead", PropertyMap(Multimaps.forMap(props))))
	}

	fun head(textureValue: String?, uuid: UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")): ItemStack {
		val stack = ItemStack(Items.PLAYER_HEAD)
		stack.set(DataComponents.PROFILE, profile(textureValue, uuid))
		return stack
	}
}

object TexConfig {
	private val config get() = TextureConfig

	fun reset() {
		config.explicitDefaultLoad()
	}

	fun set(key: String, value: Boolean) {
		@Suppress("UNCHECKED_CAST")
		(config.allOptions[key] as ManagedOption<Boolean>).set(value)
	}

	inline fun <T> with(key: String, value: Boolean, body: () -> T): T {
		set(key, value)
		try {
			return body()
		} finally {
			reset()
		}
	}
}

object Json {
	fun obj(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
}

object Fakes {

	val vanillaItemModelTypes: Unit by lazy {
		runCatching { net.minecraft.client.renderer.item.ItemModels.bootstrap() }
		Unit
	}

	private val unsafe: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
		it.isAccessible = true
		it.get(null) as Unsafe
	}

	fun <T> alloc(cls: Class<T>): T = cls.cast(unsafe.allocateInstance(cls))

	fun <T : Screen> screen(title: Component, cls: Class<T>): T {
		val screen = alloc(cls)
		val field = Screen::class.java.getDeclaredField("title")
		field.isAccessible = true
		field.set(screen, title)
		return screen
	}

	fun screen(title: Component): Screen = screen(title, GenericMessageScreen::class.java)

	fun <T : Any> runListener(listener: SimplePreparableReloadListener<T>, manager: ResourceManager): T {
		val cls = SimplePreparableReloadListener::class.java
		val prepare = cls.getDeclaredMethod("prepare", ResourceManager::class.java, ProfilerFiller::class.java)
		val apply = cls.getDeclaredMethod("apply", Any::class.java, ResourceManager::class.java, ProfilerFiller::class.java)
		prepare.isAccessible = true
		apply.isAccessible = true
		try {
			@Suppress("UNCHECKED_CAST")
			val prepared = prepare.invoke(listener, manager, InactiveProfiler.INSTANCE) as T
			apply.invoke(listener, prepared, manager, InactiveProfiler.INSTANCE)
			return prepared
		} catch (e: InvocationTargetException) {
			throw e.targetException
		}
	}
}

@Target(AnnotationTarget.FUNCTION)
annotation class ExpectedChange(val reason: String)
