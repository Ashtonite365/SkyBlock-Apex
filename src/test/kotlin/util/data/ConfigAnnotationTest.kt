// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.data

import java.io.File
import java.lang.reflect.Modifier
import java.net.JarURLConnection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig

@ExtendWith(AutoBootstrapExtension::class)
class ConfigAnnotationTest {
	private val pkg = "dev/ashtonite365/apex"

	private val baseTypes = setOf<Class<*>>(ProfileKeyedConfig::class.java)

	private fun modClassNames(): Set<String> {
		val names = sortedSetOf<String>()
		val loader = IDataHolder::class.java.classLoader
		for (url in loader.getResources(pkg)) {
			when (url.protocol) {
				"file" -> {
					val root = File(url.toURI())
					val base = root.path.removeSuffix(pkg.replace('/', File.separatorChar))
					root.walk().filter { it.isFile && it.name.endsWith(".class") }.forEach {
						names += it.path.removePrefix(base).removeSuffix(".class").replace(File.separatorChar, '.')
					}
				}
				"jar" -> (url.openConnection() as JarURLConnection).jarFile.use { jar ->
					jar.entries().asSequence().map { it.name }
						.filter { it.startsWith("$pkg/") && it.endsWith(".class") }
						.forEach { names += it.removeSuffix(".class").replace('/', '.') }
				}
			}
		}
		return names.filterTo(sortedSetOf()) {
			!it.startsWith("dev.ashtonite365.apex.test.") && !it.contains(".mixins.") && !it.endsWith("package-info")
		}
	}

	@Test
	fun everyConcreteDataHolderHasConfigAnnotation() {
		val loader = IDataHolder::class.java.classLoader
		val holders = mutableListOf<Class<*>>()
		val unloadable = mutableListOf<String>()
		for (name in modClassNames()) {
			val cls = try {
				Class.forName(name, false, loader)
			} catch (e: Throwable) {
				unloadable += "$name (${e.javaClass.simpleName})"
				continue
			}
			if (!IDataHolder::class.java.isAssignableFrom(cls)) continue
			if (cls.isInterface || Modifier.isAbstract(cls.modifiers) || cls in baseTypes) continue
			holders += cls
		}

		assertTrue(holders.size > 100, "only found ${holders.size} IDataHolder subclasses; scan broken? unloadable=$unloadable")
		assertTrue(dev.ashtonite365.apex.gui.config.DangerousSettings.TConfig::class.java in holders, "scan did not see DangerousSettings.TConfig")
		val missing = holders.filter { it.getAnnotation(Config::class.java) == null }.map { it.name }.sorted()
		assertEquals(emptyList<String>(), missing, "IDataHolder subclasses missing @Config (crash on first use)")
	}
}
