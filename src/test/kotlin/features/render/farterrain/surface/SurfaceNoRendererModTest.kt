// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawnSources
import dev.ashtonite365.apex.init.MixinPlugin
import java.io.File
import java.net.URLClassLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceNoRendererModTest {
	private val forbidden = listOf("net/caffeinemc", "dev/ashtonite365/apex/compat/sodium", "dev/ashtonite365/apex/mixins/sodium")

	private fun classRoot(c: Class<*>): File {
		val res = c.name.replace('.', '/') + ".class"
		val url = c.classLoader.getResource(res) ?: error("no class file for ${c.name}")
		require(url.protocol == "file") { "expected a class directory, got $url" }
		var f = File(url.toURI())
		repeat(c.name.count { it == '.' } + 1) { f = f.parentFile }
		return f
	}

	@Test
	fun commonFarTerrainBytecodeNeverReferencesTheRenderer() {
		val kotlinRoot = classRoot(SurfaceDrawnList::class.java)
		val dirs = listOf(
			File(kotlinRoot, "dev/ashtonite365/apex/features/render/farterrain"),
			File(classRoot(MixinPlugin::class.java), "dev/ashtonite365/apex/mixins/surfacelod"),
		)
		var checked = 0
		for (dir in dirs) {
			assertTrue(dir.isDirectory, "$dir")
			dir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.forEach { f ->
				val text = String(f.readBytes(), Charsets.ISO_8859_1)
				for (bad in forbidden) assertFalse(text.contains(bad), "${f.relativeTo(dir)} refers to $bad")
				checked++
			}
		}
		assertTrue(checked > 50, "only $checked classes found")
	}

	@Test
	fun handoffClassesLoadAndRunWithTheRendererMissing() {

		val roots = arrayOf(classRoot(SurfaceDrawnList::class.java).toURI().toURL())
		val asked = ArrayList<String>()
		val farPkg = "dev.ashtonite365.apex.features.render.farterrain."
		val guard = object : URLClassLoader(roots, SurfaceNoRendererModTest::class.java.classLoader) {
			override fun loadClass(name: String, resolve: Boolean): Class<*> {
				if (forbidden.any { name.replace('.', '/').startsWith(it) }) { asked.add(name); throw ClassNotFoundException("renderer not installed: $name") }
				if (name.startsWith(farPkg)) synchronized(getClassLoadingLock(name)) {
					return findLoadedClass(name) ?: findClass(name)
				}
				return super.loadClass(name, resolve)
			}
		}
		guard.use { cl ->
			val pkg = "dev.ashtonite365.apex.features.render.farterrain.surface."
			for (n in listOf("SurfaceDrawnList", "SurfaceDrawnSources", "SurfaceDrawnSource", "SurfaceDrawnMode", "SurfaceHandoff",
				"SurfaceHandoffGeometry", "SurfaceHandoffTimer", "SurfaceBuiltWait", "SurfaceHandoffOrder", "SurfaceHandoffGrid", "SurfaceCameraMotion",
				"SurfaceGameArea", "SurfaceHandoffDecider", "SurfaceHandoffDecider\$Probe", "SurfaceHandoffDecider\$Frame", "SurfaceFarDistance")) {
				val c = Class.forName(pkg + n, true, cl)
				assertEquals(cl, c.classLoader, n)
			}

			val sources = cl.loadClass(pkg + "SurfaceDrawnSources")
			val listC = cl.loadClass(pkg + "SurfaceDrawnList")
			val list = listC.getConstructor(Long::class.javaPrimitiveType).newInstance(100L)
			val companion = sources.getField("Companion").get(null)
			val factory = companion.javaClass.getMethod("serviceLoader", ClassLoader::class.java).invoke(companion, cl)
			val notLoaded: (String) -> Boolean = { false }
			val warn: (String, Throwable?) -> Unit = { m, _ -> error("nothing should be logged: $m") }
			val s = sources.constructors.single().newInstance(notLoaded, factory, warn)
			val collect = sources.methods.single { it.name == "collect" }
			repeat(10) { assertEquals(false, collect.invoke(s, list)) }
		}
		assertTrue(asked.isEmpty(), "renderer classes requested: $asked")
	}

	@Test
	fun aBrokenOrMissingReaderFallsBackThroughTheRealServiceLoader() {

		val logs = ArrayList<String>()
		val refusing = object : ClassLoader(SurfaceNoRendererModTest::class.java.classLoader) {
			override fun loadClass(name: String, resolve: Boolean): Class<*> {
				if (name.startsWith("dev.ashtonite365.apex.compat.sodium") || name.startsWith("net.caffeinemc")) throw ClassNotFoundException(name)
				return super.loadClass(name, resolve)
			}
		}
		val s = SurfaceDrawnSources({ true }, SurfaceDrawnSources.serviceLoader(refusing), { m, _ -> logs.add(m) })
		repeat(20) { assertFalse(s.collect(SurfaceDrawnList())) }
		assertEquals(1, logs.size, "$logs")
	}

	@Test
	fun rendererOnlyMixinsApplyOnlyWithTheirMod() {
		val none: java.util.function.Predicate<String> = java.util.function.Predicate { false }
		val all: java.util.function.Predicate<String> = java.util.function.Predicate { true }
		for (name in listOf("sodium.accessor.AccessorSodiumWorldRenderer", "dev.ashtonite365.apex.mixins.sodium.texengine.SodiumBlockModelHook")) {
			assertFalse(MixinPlugin.requiredModPresent(name, none), name)
			assertTrue(MixinPlugin.requiredModPresent(name, all), name)
		}
		for (name in listOf("surfacelod.SurfaceHandoffHoldBackMixin", "dev.ashtonite365.apex.mixins.surfacelod.SurfaceFogMixin", "compat.jade.OnUpdateBreakProgress"))
			assertTrue(MixinPlugin.requiredModPresent(name, none), name)
	}
}
