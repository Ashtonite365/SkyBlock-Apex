// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.json

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Lifecycle
import com.mojang.util.UndashedUuid
import java.util.UUID
import net.minecraft.core.UUIDUtil

object ApexCodecs {

	private fun parseLenient(text: String): DataResult<UUID> =
		runCatching { UndashedUuid.fromStringLenient(text) }.fold(
			onSuccess = { uuid -> DataResult.success(uuid, Lifecycle.stable()) },
			onFailure = { failure ->
				if (failure !is IllegalArgumentException) throw failure
				DataResult.error { "Invalid UUID $text: ${failure.message}" }
			},
		)

	private val UUID_AS_TEXT: Codec<UUID> = Codec.STRING.comapFlatMap(::parseLenient, UndashedUuid::toString)

	@JvmField
	val UUID_LENIENT_PREFER_INT_STREAM: Codec<UUID> = Codec.withAlternative(UUIDUtil.CODEC, UUID_AS_TEXT)
}
