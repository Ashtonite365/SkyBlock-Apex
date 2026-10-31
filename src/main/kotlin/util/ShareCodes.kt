package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.Apex
import java.util.Base64
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

object ShareCodes {
	private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

	@JvmStatic
	fun encodeBase64Text(raw: String): String = Base64.getEncoder().encodeToString(raw.encodeToByteArray())

	@JvmStatic
	fun decodeBase64OrNull(raw: String): String? {
		val code = raw.trim()
		if (code.any { it !in ALPHABET }) return null
		return try {
			Base64.getDecoder().decode(code).decodeToString()
		} catch (malformed: IllegalArgumentException) {
			null
		}
	}

	@JvmStatic
	fun sharePrefixOf(data: String): String? {
		val text = decodeBase64OrNull(data) ?: return null
		val end = text.indexOf('/') + 1
		return if (end == 0) null else text.take(end).ifBlank { null }
	}

	@JvmStatic
	fun base64PrefixKey(prefix: String): String {
		val groups = encodeBase64Text(prefix).trimEnd('=').chunked(4)
		return groups.filter { it.length == 4 }.joinToString("")
	}

	inline fun <reified T> toShareCode(kind: String, value: T): String = toShareCode(kind, value, serializer())

	inline fun <reified T : Any> fromShareCode(kind: String, code: String): T? = fromShareCode(kind, code, serializer())

	@JvmStatic
	fun <T> toShareCode(kind: String, value: T, writer: SerializationStrategy<T>): String {
		checkKind(kind)
		return encodeBase64Text(kind + Apex.tightJson.encodeToString(writer, value))
	}

	@JvmStatic
	fun <T : Any> fromShareCode(kind: String, code: String, reader: DeserializationStrategy<T>): T? {
		checkKind(kind)
		val trimmed = code.trim()
		if (!trimmed.startsWith(base64PrefixKey(kind))) return null
		val text = decodeBase64OrNull(trimmed)?.takeIf { it.startsWith(kind) } ?: return null
		return try {
			Apex.json.decodeFromString(reader, text.removePrefix(kind))
		} catch (unreadable: Exception) {
			null
		}
	}

	private fun checkKind(kind: String) = require(kind.endsWith('/'))
}
