// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.compress

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.random.Random
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.compress.ApexPackEncoder
import dev.ashtonite365.apex.compress.ApexPackTool
import dev.ashtonite365.apex.compress.CompressFormatException
import dev.ashtonite365.apex.compress.Decompress
import dev.ashtonite365.apex.repo.RepoBundle
import dev.ashtonite365.apex.test.compress.CompressTestData.fuzzInput
import dev.ashtonite365.apex.test.compress.CompressTestData.liveRaw
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ApexPackTest {
	@TempDir
	lateinit var tempDir: Path

	private fun opts(f: ApexPackEncoder.Options.() -> Unit = {}) = ApexPackEncoder.Options().apply(f)

	private fun roundTrip(data: ByteArray, o: ApexPackEncoder.Options = opts()): ByteArray {
		val p = ApexPackEncoder.compress(data, 0, data.size, o)
		assertTrue(ApexPack.isApexPack(p, 0, p.size))
		assertEquals(data.size.toLong(), ApexPack.contentSize(p, 0, p.size))
		assertArrayEquals(data, ApexPack.decompress(p))
		val dst = ByteArray(data.size + 7)
		assertEquals(data.size, ApexPack.decompress(p, 0, p.size, dst, 3, data.size + 4))
		assertArrayEquals(data, dst.copyOfRange(3, 3 + data.size))
		assertArrayEquals(data, Decompress.decode(p))
		assertArrayEquals(data, Decompress.open(ByteArrayInputStream(p)).use { it.readAllBytes() })
		return p
	}

	@Test
	fun edgeCases() {
		roundTrip(ByteArray(0))
		roundTrip(byteArrayOf(42))
		roundTrip("abcabcabcabcabcabcabc".encodeToByteArray())
		roundTrip(ByteArray(3) { 7 })
		val rnd = Random(1).nextBytes(100_000)
		val p = roundTrip(rnd)
		assertTrue(p.size < rnd.size + 64, "random data must fall back to stored blocks (${p.size})")
		val zeros = ByteArray(10_000_000)
		assertTrue(roundTrip(zeros).size < 2000)

		val lits = Random(2).nextBytes(300_000).let { it + ByteArray(1000) + it }
		roundTrip(lits)
		roundTrip(lits, opts { checksum = false })
		for (n in 1..64) roundTrip(Random(n).nextBytes(n) + ByteArray(n) + Random(n).nextBytes(n))
	}

	@Test
	fun repoDataAllOptions() {
		val slice = liveRaw.copyOfRange(0, 3_000_000)
		val zstd19 = CompressTestData.nativeZstd.takeIf { it }?.let { CompressTestData.nativeCompress(slice, 19).size }
		val sizes = mutableMapOf<String, Int>()
		sizes["default"] = roundTrip(slice).size
		sizes["fast"] = roundTrip(slice, ApexPackEncoder.Options.fast()).size
		sizes["small blocks"] = roundTrip(slice, opts { blockSize = 65536 }).size
		sizes["window 64k"] = roundTrip(slice, opts { window = 65536; passes = 1 }).size
		sizes["penalty 24"] = roundTrip(slice, opts { sequencePenalty = 24 }).size
		sizes["no checksum"] = roundTrip(slice, opts { checksum = false }).size
		assertEquals(sizes["default"]!! - 8, sizes["no checksum"])
		if (zstd19 != null) assertTrue(sizes["default"]!! < zstd19 * 1.05, "$sizes vs zstd-19 $zstd19")
	}

	@Test
	fun fuzzRoundTrips() {
		val r = Random(4242)
		repeat(250) {
			val data = fuzzInput(r, 150_000)
			val o = when (r.nextInt(3)) { 0 -> ApexPackEncoder.Options.fast(); 1 -> opts { blockSize = 1 + r.nextInt(70_000) }; else -> opts { sequencePenalty = r.nextInt(30); window = 1 + r.nextInt(100_000) } }
			roundTrip(data, o)
		}
	}

	@Test
	fun corruptAndTruncatedPacksFailCleanly() = assertTimeoutPreemptively(Duration.ofSeconds(240)) {
		val r = Random(99)
		val packs = listOf(
			ApexPackEncoder.compress(liveRaw.copyOfRange(0, 400_000), 0, 400_000, ApexPackEncoder.Options.fast()),
			ApexPackEncoder.compress(liveRaw.copyOfRange(5_000_000, 5_020_000), 0, 20_000, opts { blockSize = 4096 }),
			ApexPackEncoder.compress(Random(5).nextBytes(3000)),
			ApexPackEncoder.compress("tiny tiny tiny tiny".encodeToByteArray()),
		)
		var cases = 0
		for (p in packs) {
			val good = ApexPack.decompress(p)
			val cuts = if (p.size <= 600) (0 until p.size).toList() else List(300) { r.nextInt(p.size) }
			for (c in cuts) { assertThrows(CompressFormatException::class.java) { ApexPack.decompress(p.copyOf(c)) }; cases++ }
			assertThrows(CompressFormatException::class.java) { ApexPack.decompress(p + 0) }
			repeat(800) {
				val m = p.copyOf()
				repeat(1 + r.nextInt(3)) { val i = r.nextInt(m.size); m[i] = (m[i].toInt() xor (1 + r.nextInt(255))).toByte() }

				val out = try { ApexPack.decompress(m) } catch (e: CompressFormatException) { null } catch (e: Throwable) { throw AssertionError("threw ${e.javaClass.name}: ${e.message}", e) }
				if (out != null) assertArrayEquals(good, out)
				cases++
			}
		}

		val plain = ApexPackEncoder.compress(liveRaw, 0, 200_000, opts { checksum = false })
		repeat(3000) {
			val m = plain.copyOf(); val i = 6 + r.nextInt(m.size - 6); m[i] = r.nextInt(256).toByte()
			try { ApexPack.decompress(m) } catch (_: CompressFormatException) { } catch (e: Throwable) { throw AssertionError("threw ${e.javaClass.name}", e) }
			cases++
		}
		repeat(2000) {
			val g = byteArrayOf(0xAE.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 1, r.nextInt(2).toByte()) + Random(it).nextBytes(r.nextInt(200))
			try { ApexPack.decompress(g) } catch (_: CompressFormatException) { } catch (e: Throwable) { throw AssertionError("threw ${e.javaClass.name}", e) }
			cases++
		}

		val huge = byteArrayOf(0xAE.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 1, 0, -1, -1, -1, -1, 0x0F, 0)
		assertThrows(CompressFormatException::class.java) { ApexPack.decompress(huge) }
		val z5000 = ApexPackEncoder.compress(ByteArray(5000))
		assertThrows(CompressFormatException::class.java) { ApexPack.decompress(z5000, 0, z5000.size, 100) }
		assertThrows(CompressFormatException::class.java) { ApexPack.decompress(z5000, 0, z5000.size, ByteArray(4999), 0, 4999) }
		assertThrows(CompressFormatException::class.java) { ApexPack.decompress(byteArrayOf(0xAE.toByte(), 'A'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 2, 0, 0, 0)) }
		assertTrue(cases > 8000, "$cases cases")
	}

	@Test
	fun detectsBothFormats() {
		val data = liveRaw.copyOfRange(0, 50_000)
		val pack = ApexPackEncoder.compress(data)
		val zst = CompressTestData.zstdFixtures.getValue("text.L19.ck1.cs1.zst")
		assertEquals(Decompress.Format.APEX_PACK, Decompress.detect(pack, 0, pack.size))
		assertEquals(Decompress.Format.ZSTD, Decompress.detect(zst, 0, zst.size))
		val skippable = CompressTestData.zstdFixtures.getValue("text.multi.zst")
		assertEquals(Decompress.Format.ZSTD, Decompress.detect(skippable, 0, skippable.size))
		assertEquals(Decompress.Format.UNKNOWN, Decompress.detect(byteArrayOf(1, 2, 3, 4, 5), 0, 5))
		assertEquals(Decompress.Format.UNKNOWN, Decompress.detect(byteArrayOf(), 0, 0))
		assertThrows(CompressFormatException::class.java) { Decompress.decode("{}".encodeToByteArray()) }
		assertThrows(CompressFormatException::class.java) { Decompress.open(ByteArrayInputStream(byteArrayOf(9, 9))) }
	}

	@Test
	fun repoBundleReadsPack() {
		for (name in listOf("repo.json.zst", "toplevel.json.zst")) {
			val zst = tempDir.resolve("b-$name")
			zst.writeBytes(CompressTestData.resource("/apex-bundle/$name"))
			val pack = tempDir.resolve("b-$name.apack")
			pack.writeBytes(ApexPackEncoder.compress(Decompress.decode(zst.readBytes())))
			val a = tempDir.resolve("a-$name"); val b = tempDir.resolve("p-$name")
			assertEquals(RepoBundle.unpackCompressed(zst, a), RepoBundle.unpackCompressed(pack, b))
			val files = Files.walk(a).use { s -> s.filter { Files.isRegularFile(it) }.map { a.relativize(it).toString() }.sorted().toList() }
			assertTrue(files.isNotEmpty())
			for (f in files) assertArrayEquals(a.resolve(f).readBytes(), b.resolve(f).readBytes(), f)
		}
	}

	@Test
	fun encoderTool() {
		val src = tempDir.resolve("in.json.zst")
		src.writeBytes(CompressTestData.resource("/apex-bundle/repo.json.zst"))
		val out = tempDir.resolve("out.apack"); val back = tempDir.resolve("back.json")
		ApexPackTool.run(arrayOf("encode", "--fast", src.toString(), out.toString()))
		ApexPackTool.run(arrayOf("decode", out.toString(), back.toString()))
		assertArrayEquals(Decompress.decode(src.readBytes()), back.readBytes())
		ApexPackTool.run(arrayOf("info", out.toString()))
	}
}
