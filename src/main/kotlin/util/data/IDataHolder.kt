package dev.ashtonite365.apex.util.data

import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.config.storage.ConfigStorageClass
import dev.ashtonite365.apex.gui.config.storage.ApexConfigLoader
import dev.ashtonite365.apex.util.SkyBlockSession

sealed class IDataHolder<K> {
	init {
		val annotated = javaClass.isAnnotationPresent(Config::class.java)
		require(annotated)
	}

	fun markDirty(future: CompletableFuture<Void?>? = null) = ApexConfigLoader.markDirty(this, future)

	abstract val storageClass: ConfigStorageClass
	abstract fun keys(): Collection<K>
	abstract fun saveTo(key: K): JsonObject
	abstract fun loadFrom(key: K, jsonObject: JsonObject)

	abstract fun explicitDefaultLoad()

	abstract fun clear()

	open val isLoaded: Boolean get() = true

	open fun afterAllLoaded() = Unit
}

private fun <V> encodeUnder(prefix: String, serializer: KSerializer<V>, value: V): JsonObject =
	JsonObject(mapOf(prefix to Apex.json.encodeToJsonElement(serializer, value)))

private fun <V> decodeUnder(prefix: String, serializer: KSerializer<V>, saved: JsonObject): V? =
	saved[prefix]?.let { Apex.json.decodeFromJsonElement(serializer, it) }

private fun notLoaded(holder: Any): Nothing = error("Config $holder not loaded — forgot to register?")

open class ProfileKeyedConfig<T>(val prefix: String, val serializer: KSerializer<T>, val default: () -> T & Any) :
	IDataHolder<UUID>() {

	private var perProfile: MutableMap<UUID, T>? = null

	override val storageClass: ConfigStorageClass get() = ConfigStorageClass.PROFILE

	override val isLoaded: Boolean get() = perProfile != null

	val data: T & Any get() {
			val values = perProfile ?: notLoaded(this)
			val profile = SkyBlockSession.profileIdOrZero
			return values[profile] ?: default().also { values[profile] = it }
		}

	override fun keys(): Collection<UUID> = perProfile!!.keys

	override fun saveTo(key: UUID): JsonObject {
		val value = perProfile!![key] ?: return JsonObject(emptyMap())
		return encodeUnder(prefix, serializer, value)
	}

	override fun loadFrom(key: UUID, jsonObject: JsonObject) {
		val values = perProfile ?: mutableMapOf<UUID, T>().also { perProfile = it }
		values[key] = decodeUnder(prefix, serializer, jsonObject) ?: default()
	}

	override fun explicitDefaultLoad() = run { perProfile = mutableMapOf() }

	override fun clear() = run { perProfile = null }
}

abstract class GenericConfig<T>(val prefix: String, val serializer: KSerializer<T>, val default: () -> T) :
	IDataHolder<Unit>() {
	private var current: T? = null

	override val isLoaded: Boolean get() = current != null

	val data get() = current ?: notLoaded(this)

	override fun keys(): Collection<Unit> = listOf(Unit)

	open fun onLoad() = Unit

	override fun saveTo(key: Unit): JsonObject = encodeUnder(prefix, serializer, data)

	override fun loadFrom(key: Unit, jsonObject: JsonObject) {
		current = decodeUnder(prefix, serializer, jsonObject) ?: default()
		onLoad()
	}

	override fun explicitDefaultLoad() = run { current = default() }

	override fun clear() = run { current = null }
}
