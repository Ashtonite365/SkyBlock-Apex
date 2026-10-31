// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.compress

import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Duration
import kotlin.random.Random
import dev.ashtonite365.apex.compress.CompressFormatException
import dev.ashtonite365.apex.compress.Zstd
import dev.ashtonite365.apex.compress.ZstdInputStream
import dev.ashtonite365.apex.test.compress.CompressTestData.airDecode
import dev.ashtonite365.apex.test.compress.CompressTestData.fuzzInput
import dev.ashtonite365.apex.test.compress.CompressTestData.liveRaw
import dev.ashtonite365.apex.test.compress.CompressTestData.nativeCompress
import dev.ashtonite365.apex.test.compress.CompressTestData.sha256
import io.airlift.compress.v3.zstd.ZstdJavaCompressor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class ZstdDecoderTest {
	private fun ours(z: ByteArray): ByteArray {
		val one = Zstd.decompress(z)
		val streamed = ZstdInputStream(ByteArrayInputStream(z)).use { it.readAllBytes() }
		assertArrayEquals(one, streamed, "one-shot and streaming disagree")
		return one
	}

	@Test
	fun libzstdFixturesMatchOracleAndSources() {
		val fx = CompressTestData.zstdFixtures
		assertTrue(fx.size >= 70, "only ${fx.size} fixtures")
		for ((name, z) in fx) {
			val got = ours(z)
			val (sha, len) = CompressTestData.fixtureSources.getValue(name.substringBefore('.'))
			assertEquals(len, got.size, name)
			assertEquals(sha, sha256(got), name)

			if (!name.contains("multi")) assertArrayEquals(airDecode(z), got, name)
		}
	}

	@Test
	fun longWindowFrameAndWindowLimit() {
		val far = CompressTestData.zstdFixtures.getValue("far.W24.zst")
		assertEquals(9568256, ours(far).size)

		assertThrows(CompressFormatException::class.java) { Zstd.decompress(far, 0, far.size, ByteArray(9568256), 0, 9568256, 1L shl 20) }
		assertThrows(IOException::class.java) { ZstdInputStream(ByteArrayInputStream(far), 1L shl 20).use { it.readAllBytes() } }
	}

	@Test
	fun repoBundlesMatchOracle() {
		for (p in listOf("/apex-bundle/live/repo.json.zst", "/apex-bundle/repo.json.zst", "/apex-bundle/r89e4ebb5/repo.json.zst", "/apex-bundle/toplevel.json.zst")) {
			val z = CompressTestData.resource(p)
			assertArrayEquals(airDecode(z), ours(z), p)
		}
		assertEquals(35317039, liveRaw.size)
	}

	@Test
	fun nativeLevelsOnRepoData() {
		assumeTrue(CompressTestData.nativeZstd, "libzstd not loadable here")
		val head = liveRaw.copyOfRange(0, 1 shl 20)
		val mid = liveRaw.copyOfRange(liveRaw.size / 2, liveRaw.size / 2 + (1 shl 19))
		for (level in (-5..-1) + (1..19) + 22) for (data in listOf(head, mid)) {
			assertArrayEquals(data, ours(nativeCompress(data, level)), "level $level")
		}
	}

	@Test
	fun nativeFullRepo() {
		assumeTrue(CompressTestData.nativeZstd, "libzstd not loadable here")
		for (level in listOf(3, 19)) assertArrayEquals(liveRaw, ours(nativeCompress(liveRaw, level)), "level $level")
	}

	@Test
	fun fuzzRoundTripsThroughLibzstdAndJavaCompressor() {
		val r = Random(1028)
		val java = ZstdJavaCompressor()
		var n = 0
		repeat(400) {
			val data = fuzzInput(r, 200_000)
			if (CompressTestData.nativeZstd) {
				val level = 1 + r.nextInt(19)
				assertArrayEquals(data, ours(nativeCompress(data, level)), "case $it level $level"); n++
			}
			val out = ByteArray(java.maxCompressedLength(data.size))
			val z = out.copyOf(java.compress(data, 0, data.size, out, 0, out.size))
			assertArrayEquals(data, ours(z), "java case $it"); n++
		}
		assertTrue(n >= 400)
	}

	@Test
	fun corruptAndTruncatedInputFailsCleanly() = assertTimeoutPreemptively(Duration.ofSeconds(240)) {
		val r = Random(8878)
		val inputs = CompressTestData.zstdFixtures.filterKeys { !it.startsWith("far") }.values.toMutableList()
		if (CompressTestData.nativeZstd) inputs += nativeCompress(liveRaw.copyOfRange(0, 200_000), 19)
		var cases = 0
		for (z in inputs) {
			val good = Zstd.decompress(z)

			val cuts = if (z.size <= 600) (0 until z.size).toList() else List(200) { r.nextInt(z.size) }
			for (c in cuts) { expectCleanFailureOrSame(z.copyOf(c), good, prefix = true); cases++ }
			repeat(60) {
				val m = z.copyOf()
				when (r.nextInt(3)) {
					0 -> { val i = r.nextInt(m.size); m[i] = (m[i].toInt() xor (1 shl r.nextInt(8))).toByte() }
					1 -> { val i = r.nextInt(m.size); m[i] = r.nextInt(256).toByte() }
					else -> repeat(1 + r.nextInt(8)) { val i = r.nextInt(m.size); m[i] = r.nextInt(256).toByte() }
				}
				expectCleanFailureOrSame(m, good, prefix = false); cases++
			}
		}

		repeat(2000) { expectCleanFailureOrSame(Random(it).nextBytes(r.nextInt(300)), null, prefix = false); cases++ }
		val hugeContent = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0xE0.toByte(), 0, 0, 0, 0, 0, 0, 0, 0x7F)
		assertThrows(CompressFormatException::class.java) { Zstd.decompress(hugeContent) }
		val hugeWindow = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0x00, 0xF8.toByte(), 1, 0, 0)
		assertThrows(IOException::class.java) { ZstdInputStream(ByteArrayInputStream(hugeWindow)).use { it.readAllBytes() } }
		assertTrue(cases > 10_000, "$cases cases")
	}

	private fun expectCleanFailureOrSame(input: ByteArray, good: ByteArray?, prefix: Boolean) {
		val one = try { Zstd.decompress(input) } catch (e: CompressFormatException) { null } catch (e: Throwable) { throw AssertionError("one-shot threw ${e.javaClass.name}: ${e.message}", e) }
		val streamed = try { ZstdInputStream(ByteArrayInputStream(input)).use { it.readAllBytes() } } catch (e: IOException) { null } catch (e: Throwable) { throw AssertionError("stream threw ${e.javaClass.name}: ${e.message}", e) }
		if (prefix && good != null && good.isNotEmpty()) {

			if (one != null) assertTrue(one.size <= good.size)
		}
		if (one != null && streamed != null) assertArrayEquals(one, streamed)
	}
}
