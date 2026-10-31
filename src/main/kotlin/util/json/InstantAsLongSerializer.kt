// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.json

import java.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object InstantAsLongSerializer
	: KSerializer<Instant> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
		"InstantAsLongSerializer",
		PrimitiveKind.LONG,
	)

	override fun serialize(encoder: Encoder, value: Instant) = value.toEpochMilli().let(encoder::encodeLong)

	override fun deserialize(decoder: Decoder): Instant = decoder.decodeLong().let(Instant::ofEpochMilli)
}
