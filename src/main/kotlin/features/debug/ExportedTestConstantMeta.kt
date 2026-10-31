// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.debug

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional
import net.minecraft.SharedConstants
import dev.ashtonite365.apex.Apex

data class ExportedTestConstantMeta(val dataVersion: Int, val modVersion: Optional<String>) {
	companion object {

		val current: ExportedTestConstantMeta = stampNow()

		private fun stampNow() = ExportedTestConstantMeta(SharedConstants.getCurrentVersion().dataVersion().version, Optional.of("Apex " + Apex.version.friendlyString))

		val CODEC: Codec<ExportedTestConstantMeta> = RecordCodecBuilder.create { fields ->
			val data = Codec.INT.fieldOf("dataVersion").forGetter<ExportedTestConstantMeta> { it.dataVersion }
			val mod = Codec.STRING.optionalFieldOf("modVersion").forGetter<ExportedTestConstantMeta> { it.modVersion }
			fields.group(data, mod).apply(fields) { version, origin -> ExportedTestConstantMeta(version, origin) }
		}

		val SOURCE_CODEC: Codec<ExportedTestConstantMeta> = CODEC.fieldOf("source").codec()
	}
}
