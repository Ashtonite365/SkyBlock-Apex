package dev.ashtonite365.apex.util

import java.util.Base64

object Base64Util {
	private val decoder: Base64.Decoder = Base64.getDecoder()
	private val encoder: Base64.Encoder = Base64.getEncoder()

	fun decodeString(str: String): String = String(decodeBytes(str), Charsets.UTF_8)

	fun decodeBytes(str: String): ByteArray = decoder.decode(str.padToValidBase64())

	fun String.padToValidBase64(): String {
		val missing = (4 - length % 4) % 4
		return if (missing == 0) this else padEnd(length + missing, '=')
	}

	fun encodeToString(bytes: ByteArray): String = encoder.encodeToString(bytes)
}
