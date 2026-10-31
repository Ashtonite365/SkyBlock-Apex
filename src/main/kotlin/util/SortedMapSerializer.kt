package dev.ashtonite365.apex.util

import java.util.SortedMap
import java.util.TreeMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class SortedMapSerializer<K : Comparable<K>, V>(val keyDelegate: KSerializer<K>, val valueDelegate: KSerializer<V>) :
	KSerializer<SortedMap<K, V>> {
	private val plain: KSerializer<Map<K, V>> = MapSerializer(keyDelegate, valueDelegate)

	override val descriptor: SerialDescriptor get() = plain.descriptor

	override fun serialize(encoder: Encoder, value: SortedMap<K, V>) = plain.serialize(encoder, value)

	override fun deserialize(decoder: Decoder): SortedMap<K, V> {
		val sorted = TreeMap<K, V>(Comparator.naturalOrder())
		sorted.putAll(plain.deserialize(decoder))
		return sorted
	}
}
