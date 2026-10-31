// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.config.storage

import java.io.File
import kotlinx.serialization.json.JsonObject
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.util.data.GenericConfig
import dev.ashtonite365.apex.util.data.IDataHolder
import dev.ashtonite365.apex.util.data.ProfileKeyedConfig

object MainConfigRegistry {
	val loader: ClassLoader get() = IDataHolder::class.java.classLoader

	val mainRoots: List<File> by lazy {
		listOf(IDataHolder::class.java, dev.ashtonite365.apex.init.MixinPlugin::class.java)
			.map { File(it.protectionDomain.codeSource.location.toURI()) }
			.distinct()
	}

	val registered: List<IDataHolder<*>> by lazy {
		val provider = mainRoots.map { File(it, "dev/ashtonite365/apex/annotations/generated/main/ConfigProvider.class") }
			.single { it.isFile }
		val order = mutableListOf<String>()
		ClassReader(provider.readBytes()).accept(object : ClassVisitor(Opcodes.ASM9) {
			override fun visitMethod(a: Int, n: String, d: String, s: String?, e: Array<out String>?): MethodVisitor =
				object : MethodVisitor(Opcodes.ASM9) {
					override fun visitFieldInsn(op: Int, owner: String, n: String, d: String) {
						if (op == Opcodes.GETSTATIC && n == "INSTANCE") order += owner.replace('/', '.')
					}
				}
		}, 0)
		order.distinct().map { Class.forName(it, true, loader).getField("INSTANCE").get(null) as IDataHolder<*> }
	}

	val registeredClasses: Set<Class<*>> by lazy { registered.mapTo(hashSetOf()) { it.javaClass } }

	fun isLoaded(h: IDataHolder<*>): Boolean = when (h) {
		is GenericConfig<*> -> runCatching { h.data }.isSuccess
		is ProfileKeyedConfig<*> -> runCatching { h.keys() }.isSuccess
		else -> true
	}

	fun snapshot(): Map<IDataHolder<*>, List<Pair<Any, JsonObject>>?> =
		registered.filter { it.storageClass != ConfigStorageClass.CONFIG }.associateWith { h ->
			if (!isLoaded(h)) null
			else {
				@Suppress("UNCHECKED_CAST")
				val hh = h as IDataHolder<Any>
				runCatching { hh.keys().map { it to hh.saveTo(it) } }.getOrNull()
			}
		}

	fun restore(state: Map<IDataHolder<*>, List<Pair<Any, JsonObject>>?>) {
		for ((h, saved) in state) {
			@Suppress("UNCHECKED_CAST")
			val hh = h as IDataHolder<Any>
			hh.clear()
			saved?.forEach { (k, json) -> runCatching { hh.loadFrom(k, json) } }
		}

		dev.ashtonite365.apex.features.world.ApexWaypoints.pendingSave = false
	}
}
