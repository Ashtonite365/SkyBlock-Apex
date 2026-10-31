// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import com.google.common.collect.Multimaps
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.Base64Util.padToValidBase64
import dev.ashtonite365.apex.util.repoItemId

object LearnedSkullTextures {
	@Serializable
	data class Data(val textures: MutableMap<String, Learned> = mutableMapOf())

	@Serializable
	data class Learned(val uuid: String, val value: String)

	@Config
	object Store : DataHolder<Data>(serializer(), "learned-skull-textures", { Data() })

	fun liveTexture(stack: ItemStack): Learned? {
		if (stack.item != Items.PLAYER_HEAD) return null
		val profile = stack.get(DataComponents.PROFILE)?.partialProfile() ?: return null
		val value = profile.properties()["textures"].firstOrNull()?.value()?.takeIf { it.isNotBlank() } ?: return null
		return Learned(profile.id().toString(), value)
	}

	fun needsTexture(stack: ItemStack): Boolean =
		stack.item == Items.PLAYER_HEAD && stack.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures").isNullOrEmpty()

	fun applyTo(stack: ItemStack, learned: Learned?): Boolean {
		if (learned == null || !needsTexture(stack)) return false
		stack.set(DataComponents.PROFILE, profileOf(learned))
		return true
	}

	fun profileOf(learned: Learned): net.minecraft.world.item.component.ResolvableProfile {
		val uuid = runCatching { UUID.fromString(learned.uuid) }.getOrElse { UUID.nameUUIDFromBytes(learned.value.toByteArray()) }
		val textures = Property("textures", learned.value.padToValidBase64())
		return ResolvableProfile.createResolved(GameProfile(uuid, "LameGuy123", PropertyMap(Multimaps.forMap(mapOf("textures" to textures)))))
	}

	private val log = org.apache.logging.log4j.LogManager.getLogger("Apex.LearnedSkullTextures")
	@Volatile private var broken = false

	private inline fun <R> safe(fallback: R, block: () -> R): R {
		if (broken) return fallback
		return try { block() } catch (t: Throwable) {
			broken = true
			log.warn("[Apex] learned skull textures disabled for this session: $t")
			fallback
		}
	}

	fun get(id: String): Learned? = safe(null) { Store.data.textures[id] }

	fun learn(stack: ItemStack, repoNeedsTexture: (String) -> Boolean): Boolean = safe(false) { learn0(stack, repoNeedsTexture) }

	private fun learn0(stack: ItemStack, repoNeedsTexture: (String) -> Boolean): Boolean {
		val id = stack.view().repoItemId?.skyblockId ?: return false
		val live = liveTexture(stack) ?: return false
		val map = Store.data.textures
		if (map[id] == live || !repoNeedsTexture(id)) return false
		map[id] = live
		Store.markDirty()
		return true
	}

	fun put(id: String, learned: Learned): Boolean = safe(false) {
		val map = Store.data.textures
		if (map[id] == learned) return@safe false
		map[id] = learned
		Store.markDirty()
		true
	}

	@Subscribe
	fun onInventory(event: ChestInventoryUpdateEvent) {
		val stacks = when (event) {
			is ChestInventoryUpdateEvent.Single -> listOf(event.stack)
			is ChestInventoryUpdateEvent.Multi -> event.contents
		}
		safe(Unit) { for (s in stacks) if (learn(s, ItemCache::repoHeadNeedsTexture)) s.view().repoItemId?.let { ItemCache.forget(it.skyblockId) } }
	}
}
