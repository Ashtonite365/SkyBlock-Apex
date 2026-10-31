// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.json

import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import dev.ashtonite365.apex.util.parsePotentiallyDashlessUUID

object DashlessUUIDSerializer
	: KSerializer<UUID> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
		"DashlessUUIDSerializer",
		PrimitiveKind.STRING,
	)

	override fun serialize(encoder: Encoder, value: UUID) =
		encoder.encodeString(value.toString().filterNot { ch -> ch == '-' })

	override fun deserialize(decoder: Decoder): UUID = parsePotentiallyDashlessUUID(decoder.decodeString())
}
