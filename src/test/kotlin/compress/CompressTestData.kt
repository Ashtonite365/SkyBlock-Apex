// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.compress

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random
import io.airlift.compress.v3.zstd.ZstdInputStream as AirInputStream
import io.airlift.compress.v3.zstd.ZstdNativeCompressor

object CompressTestData {
	fun resource(path: String): ByteArray = javaClass.getResourceAsStream(path)?.readAllBytes() ?: error("missing $path")

	val liveRaw: ByteArray by lazy { airDecode(resource("/apex-bundle/live/repo.json.zst")) }

	val zstdFixtures: Map<String, ByteArray> by lazy {
		val dir = File(javaClass.getResource("/compress/zstd/sources.sha256")!!.toURI()).parentFile
		dir.listFiles()!!.filter { it.name.endsWith(".zst") }.sortedBy { it.name }.associate { it.name to it.readBytes() }
	}

	val fixtureSources: Map<String, Pair<String, Int>> by lazy {
		resource("/compress/zstd/sources.sha256").decodeToString().lines().filter { it.isNotBlank() }
			.associate { l -> l.split(' ').let { it[0] to (it[1] to it[2].toInt()) } }
	}

	fun airDecode(zst: ByteArray): ByteArray = AirInputStream(ByteArrayInputStream(zst)).use { it.readAllBytes() }

	val nativeZstd: Boolean by lazy { runCatching { ZstdNativeCompressor.isEnabled() }.getOrDefault(false) }

	fun nativeCompress(data: ByteArray, level: Int): ByteArray {
		val c = ZstdNativeCompressor(level)
		val out = ByteArray(c.maxCompressedLength(data.size))
		return out.copyOf(c.compress(data, 0, data.size, out, 0, out.size))
	}

	fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

	fun fuzzInput(r: Random, maxLen: Int): ByteArray {
		val n = if (r.nextInt(4) == 0) r.nextInt(64) else r.nextInt(maxLen)
		val out = ByteArray(n)
		var i = 0
		while (i < n) {
			val len = minOf(n - i, 1 + r.nextInt(if (r.nextBoolean()) 40 else 4000))
			when (r.nextInt(5)) {
				0 -> for (k in 0 until len) out[i + k] = r.nextInt(256).toByte()
				1 -> { val b = r.nextInt(256).toByte(); for (k in 0 until len) out[i + k] = b }
				2 -> if (i > 0) { val d = 1 + r.nextInt(minOf(i, if (r.nextBoolean()) 16 else i)); for (k in 0 until len) out[i + k] = out[i + k - d] }
				3 -> { val a = r.nextInt(4).let { 1 + it }; for (k in 0 until len) out[i + k] = ('a' + r.nextInt(a)).code.toByte() }
				else -> { val s = liveRaw; val o = r.nextInt(s.size - len); System.arraycopy(s, o, out, i, len) }
			}
			i += len
		}
		return out
	}
}
