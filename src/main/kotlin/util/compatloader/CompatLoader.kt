// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.compatloader

import java.util.ServiceLoader
import kotlin.reflect.KClass
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager

open class CompatLoader<T : Any>(val serviceType: Class<T>) {
	constructor(serviceType: KClass<T>) : this(serviceType.java)

	@Retention(AnnotationRetention.RUNTIME)
	@Target(AnnotationTarget.CLASS)
	@Repeatable
	annotation class RequireMod(val value: String)

	private val log = LogManager.getLogger("Apex.CompatLoader")

	val loader: ServiceLoader<T> = ServiceLoader.load(serviceType)

	val allValidInstances: List<T> by lazy {
		loader.reload()
		val accepted = ArrayList<T>()

		for (candidate in loader.stream()) {
			val type = candidate.type()
			if (!accepts(type)) continue
			val made = try {
				candidate.get()
			} catch (e: Exception) {
				log.error("Could not load desired instance ${type.name} for ${serviceType.name}", e)
				null
			}
			if (made != null) accepted.add(made)
		}
		accepted
	}

	val singleInstance: T? get() = allValidInstances.takeIf { it.size == 1 }?.first()

	private fun accepts(type: Class<out T>): Boolean =
		try {
			shouldLoad(type)
		} catch (e: Exception) {
			log.error("Could not determine whether to load a ${serviceType.name} subclass", e)
			false
		}

	open fun shouldLoad(type: Class<out T>): Boolean = checkRequiredModsPresent(type)

	fun checkRequiredModsPresent(type: Class<*>): Boolean {
		val loaderApi = FabricLoader.getInstance()
		return type.getAnnotationsByType(RequireMod::class.java).all { loaderApi.isModLoaded(it.value) }
	}
}
