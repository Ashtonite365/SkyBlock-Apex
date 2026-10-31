// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.gui.config.storage

import java.io.File
import java.lang.reflect.Modifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.test.testutil.RepoFile
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig

@ExtendWith(AutoBootstrapExtension::class)
class ConfigRegistrationLoadTest {
	private val registered get() = MainConfigRegistry.registered
	private val registeredClasses get() = MainConfigRegistry.registeredClasses
	private val mainRoots get() = MainConfigRegistry.mainRoots
	private fun isLoaded(h: IDataHolder<*>) = MainConfigRegistry.isLoaded(h)
	private fun snapshot() = MainConfigRegistry.snapshot()
	private fun restore(state: Map<IDataHolder<*>, List<Pair<Any, JsonObject>>?>) = MainConfigRegistry.restore(state)

	private class ClassInfo(val name: String, val isMixin: Boolean, val referenced: Set<String>)

	private val mainClasses: List<ClassInfo> by lazy {
		mainRoots.flatMap { root ->
			require(root.isDirectory) { "main classes root is not a directory: $root" }
			root.walk().filter { it.isFile && it.name.endsWith(".class") }.map { f -> readClass(f.readBytes()) }.toList()
		}
	}

	private fun readClass(bytes: ByteArray): ClassInfo {
		val refs = hashSetOf<String>()
		var name = ""
		var mixin = false
		fun addType(t: Type) {
			when (t.sort) {
				Type.OBJECT -> refs += t.internalName
				Type.ARRAY -> addType(t.elementType)
				Type.METHOD -> { addType(t.returnType); t.argumentTypes.forEach(::addType) }
			}
		}
		fun addConst(c: Any?) {
			when (c) {
				is Type -> addType(c)
				is Handle -> refs += c.owner
			}
		}
		ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
			override fun visit(v: Int, a: Int, n: String, s: String?, sup: String?, i: Array<out String>?) {
				name = n
			}

			override fun visitAnnotation(descriptor: String, visible: Boolean) = null.also {
				if (descriptor == "Lorg/spongepowered/asm/mixin/Mixin;") mixin = true
			}

			override fun visitMethod(a: Int, n: String, d: String, s: String?, e: Array<out String>?): MethodVisitor =
				object : MethodVisitor(Opcodes.ASM9) {
					override fun visitFieldInsn(op: Int, owner: String, n: String, d: String) { refs += owner }
					override fun visitMethodInsn(op: Int, owner: String, n: String, d: String, itf: Boolean) { refs += owner }
					override fun visitTypeInsn(op: Int, type: String) { refs += type }
					override fun visitLdcInsn(value: Any?) = addConst(value)
					override fun visitInvokeDynamicInsn(n: String, d: String, bsm: Handle, vararg args: Any?) {
						args.forEach(::addConst)
					}
				}
		}, ClassReader.SKIP_FRAMES)
		return ClassInfo(name.replace('/', '.'), mixin, refs.mapTo(hashSetOf()) { it.replace('/', '.') })
	}

	private val loader get() = MainConfigRegistry.loader

	private fun configClass(name: String): Class<*>? {
		if (!name.startsWith("dev.ashtonite365.apex.")) return null
		val cls = try {
			Class.forName(name, false, loader)
		} catch (_: Throwable) {
			return null
		}
		if (!IDataHolder::class.java.isAssignableFrom(cls)) return null
		if (cls.isInterface || Modifier.isAbstract(cls.modifiers) || cls == ProfileKeyedConfig::class.java) return null
		return cls
	}

	private fun sourceConfigNames(): List<String> {
		val decl = Regex("""@(?:dev\.ashtonite365\.apex\.util\.data\.)?Config(\([^)]*\))?(?:\s*//[^\n]*)?\s+(?:(?:internal|private|public|data)\s+)*object\s+(\w+)""")
		val files = listOf("src/main/kotlin", "src/main/java").map { RepoFile(it) }
			.flatMap { d -> d.walk().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }.toList() }
		assertTrue(files.size > 500, "source scan saw only ${files.size} files under src/main")
		return files.flatMap { f -> decl.findAll(f.readText()).map { it.groupValues[2] }.toList() }
	}

	@Test
	fun everyConfigObjectInTheSourceIsRegistered() {
		assertTrue(registered.size > 150, "only ${registered.size} configs registered; registry enumeration broken")

		val compiled = mainClasses.mapNotNull { configClass(it.name) }
		assertTrue(compiled.size > 150, "class scan found only ${compiled.size} config classes in $mainRoots")
		val notAnnotated = compiled.filter { it.getAnnotation(Config::class.java) == null }.map { it.name }
		assertEquals(emptyList<String>(), notAnnotated, "config classes without @Config")
		val unregistered = compiled.filter { it !in registeredClasses }.map { it.name }.sorted()
		assertEquals(emptyList<String>(), unregistered, "config classes that are never registered (crash on first use)")

		val src = sourceConfigNames().sorted()
		assertTrue(src.size > 150, "source scan found only ${src.size} @Config objects")
		assertEquals(src, compiled.map { it.simpleName }.sorted(), "@Config objects in src/main vs compiled main classes")

		assertEquals(registered.size, registeredClasses.size, "a config is registered twice")
	}

	@Test
	fun subscribersAndMixinsOnlyUseRegisteredConfigs() {
		val subscriptionClasses = mainClasses.filter { it.name.startsWith("dev.ashtonite365.apex.annotations.generated.main.AllSubscriptions") }
		assertTrue(subscriptionClasses.isNotEmpty(), "generated AllSubscriptionsMain not found in $mainRoots")

		val subscriberNames = subscriptionClasses.flatMapTo(hashSetOf()) { it.referenced }
			.filter { it.startsWith("dev.ashtonite365.apex.") && !it.startsWith("dev.ashtonite365.apex.annotations.") && !it.startsWith("dev.ashtonite365.apex.events.") }
			.toSet()
		val byName = mainClasses.associateBy { it.name }
		val subscribers = subscriberNames.mapNotNull { byName[it] }
		val mixins = mainClasses.filter { it.isMixin }
		val srcSubscribes = RepoFile("src/main/kotlin").walk().filter { it.isFile && it.name.endsWith(".kt") }
			.sumOf { f -> Regex("""@Subscribe\b""").findAll(f.readText()).count() }
		assertTrue(srcSubscribes > 200, "source scan found only $srcSubscribes @Subscribe handlers")
		assertTrue(subscribers.size > 100, "only ${subscribers.size} subscriber classes found")
		assertTrue(mixins.size > 100, "only ${mixins.size} mixin classes found")

		fun badRefs(classes: List<ClassInfo>): Pair<Int, List<String>> {
			var refs = 0
			val bad = mutableListOf<String>()
			for (c in classes) for (r in c.referenced) {
				val cfg = configClass(r) ?: continue
				refs++
				if (cfg !in registeredClasses) bad += "${c.name} -> ${cfg.name}"
			}
			return refs to bad.sorted()
		}
		val (subRefs, subBad) = badRefs(subscribers)
		val (mixRefs, mixBad) = badRefs(mixins)
		val (allRefs, allBad) = badRefs(mainClasses)
		assertTrue(subRefs > 100, "subscriber classes reference only $subRefs config classes; scan broken?")
		assertTrue(mixRefs > 0, "mixins reference no config classes; scan broken?")
		assertTrue(allRefs > subRefs, "whole-mod scan saw only $allRefs config references")
		assertEquals(emptyList<String>(), subBad, "event subscribers using unregistered configs")
		assertEquals(emptyList<String>(), mixBad, "mixins using unregistered configs")
		assertEquals(emptyList<String>(), allBad, "main classes using unregistered configs")

		assertTrue(mixins.any { "dev.ashtonite365.apex.features.items.ItemCustomizer" in it.referenced }, "no mixin calls ItemCustomizer")
	}

	private val coldStorageData = buildJsonObject {
		put("custom-chat-commands", buildJsonObject { put("legacyImported", JsonPrimitive(false)) })
	}

	@Test
	fun everyRegisteredConfigLoadsOnItsOwnFromAColdStart() {
		val state = snapshot()
		try {
			val holders = registered.filter { it.storageClass != ConfigStorageClass.CONFIG }
			val storage = holders.count { it.storageClass == ConfigStorageClass.STORAGE }
			assertTrue(storage > 20 && holders.size - storage > 20, "storage=$storage profile=${holders.size - storage}")
			val problems = mutableListOf<String>()
			for (h in holders) {
				holders.forEach { it.clear() }
				@Suppress("UNCHECKED_CAST")
				val hh = h as IDataHolder<Any>
				val key: Any = if (h.storageClass == ConfigStorageClass.PROFILE) SkyBlockSession.NULL_UUID else Unit
				val data = if (h.storageClass == ConfigStorageClass.STORAGE) coldStorageData else JsonObject(mapOf())
				try {
					hh.loadFrom(key, data)
				} catch (e: Throwable) {
					problems += "${h.javaClass.name}: load threw $e"
					continue
				}
				if (!isLoaded(h)) problems += "${h.javaClass.name}: not loaded after its own load"
			}
			assertEquals(emptyList<String>(), problems, "configs that cannot load before the others (they abort the game's config load)")

			holders.forEach { it.clear() }
			for (h in holders) {
				@Suppress("UNCHECKED_CAST")
				val hh = h as IDataHolder<Any>
				val key: Any = if (h.storageClass == ConfigStorageClass.PROFILE) SkyBlockSession.NULL_UUID else Unit
				runCatching { hh.loadFrom(key, if (h.storageClass == ConfigStorageClass.STORAGE) coldStorageData else JsonObject(mapOf())) }
			}
			for (h in listOf<IDataHolder<*>>(
				dev.ashtonite365.apex.features.items.ItemCustomizer.DConfig,
				dev.ashtonite365.apex.features.chat.ChatExtras.DConfig,
				dev.ashtonite365.apex.features.apexmarket.FlipHistory.DConfig,
				dev.ashtonite365.apex.features.auction.BazaarOrders.DConfig,
				dev.ashtonite365.apex.features.events.helpers.EventHelpers.DConfig,
				dev.ashtonite365.apex.features.fishing.FishingTracker.DConfig,
				dev.ashtonite365.apex.features.islands.JerryHelper.DConfig,
				dev.ashtonite365.apex.features.customchatcommands.CustomChatCommandsData.DConfig,
				dev.ashtonite365.apex.features.debug.DebugLogger.EnabledLogs,
			)) {
				assertTrue(h in registered, "${h.javaClass.name} not registered")
				assertTrue(isLoaded(h), "${h.javaClass.name} not loaded")
			}
		} finally {
			restore(state)
		}
	}
}
