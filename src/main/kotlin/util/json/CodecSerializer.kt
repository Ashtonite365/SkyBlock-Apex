// SPDX-License-Identifier: GPL-3.0-or-later
package util.json

import com.mojang.serialization.Codec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import dev.ashtonite365.apex.util.json.KJsonOps

abstract class CodecSerializer<T>(val codec: Codec<T>)
	: KSerializer<T> {
	private val tree: KSerializer<JsonElement> get() = JsonElement.serializer()

	override val descriptor: SerialDescriptor get() = tree.descriptor

	override fun serialize(encoder: Encoder, value: T) =
		encoder.encodeSerializableValue(tree, codec.encodeStart(KJsonOps.INSTANCE, value).getOrThrow())

	override fun deserialize(decoder: Decoder): T =
		decoder.decodeSerializableValue(tree).let { json -> codec.decode(KJsonOps.INSTANCE, json).getOrThrow().first }
}
