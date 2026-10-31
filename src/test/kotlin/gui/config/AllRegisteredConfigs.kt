package dev.ashtonite365.apex.test.gui.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import dev.ashtonite365.apex.util.data.ManagedConfig

object AllRegisteredConfigs {
	private fun buildDir(): Path {
		var p: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
		while (p != null) {
			if (p.resolve("build/generated/ksp/main").exists()) return p.resolve("build")
			if (p.fileName?.toString() == "build" && p.resolve("generated/ksp/main").exists()) return p
			p = p.parent
		}
		error("Could not find build/generated/ksp/main from ${System.getProperty("user.dir")}")
	}

	private fun loadObject(fqn: String): Any? {
		val parts = fqn.split('.')

		for (split in parts.indices.reversed()) {
			val name = parts.take(split + 1).joinToString(".") +
				parts.drop(split + 1).joinToString("") { "$$it" }
			val cls = try {
				Class.forName(name)
			} catch (_: ClassNotFoundException) {
				continue
			} catch (_: NoClassDefFoundError) {
				continue
			}
			return cls.getField("INSTANCE").get(null)
		}
		return null
	}

	val providerEntries: List<Pair<String, String>> by lazy {
		val ksp = buildDir().resolve("generated/ksp")
		Files.list(ksp).use { dirs ->
			dirs.toList().filter { it.fileName.toString() !in setOf("test", "gametest") }.flatMap { set ->
				Files.walk(set.resolve("kotlin")).use { files ->
					files.toList().filter { it.fileName.toString() == "ConfigProvider.kt" }.flatMap { f ->
						val list = Regex("listOf\\((.*)\\)").find(f.readText())?.groupValues?.get(1) ?: ""
						list.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { set.fileName.toString() to it }
					}
				}
			}
		}
	}

	val loadFailures = mutableListOf<String>()

	val holders: List<dev.ashtonite365.apex.util.data.IDataHolder<*>> by lazy {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		(providerEntries.mapNotNull { (_, fqn) ->
			runCatching { loadObject(fqn) }.getOrNull() as? dev.ashtonite365.apex.util.data.IDataHolder<*>
		} + configs).distinct()
	}

	val configs: List<ManagedConfig> by lazy {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		providerEntries.mapNotNull { (set, fqn) ->
			try {
				loadObject(fqn).also { if (it == null) loadFailures += "$set:$fqn (not on classpath)" } as? ManagedConfig
			} catch (e: Throwable) {
				loadFailures += "$set:$fqn ($e)"; null
			}
		}
			.let { (it + ManagedConfig.everyHolder.getAll()).distinct() }
	}
}
