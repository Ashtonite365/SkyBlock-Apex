package dev.ashtonite365.apex.test.features.auction

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.zip.GZIPOutputStream
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import dev.ashtonite365.apex.compress.ApexPack
import dev.ashtonite365.apex.features.auction.PriceHistory

class PriceHistoryPackTest {
	@TempDir
	lateinit var folder: Path

	private fun gz(text: String): ByteArray {
		val out = java.io.ByteArrayOutputStream()
		GZIPOutputStream(out).use { it.write(text.encodeToByteArray()) }
		return out.toByteArray()
	}

	@Test
	fun newFilesArePacks() {
		PriceHistory.append(folder, mapOf("A" to PriceHistory.Point(5, 1.0)), Instant.parse("2026-10-28T01:00:00Z"), 7)
		val file = folder.listDirectoryEntries().single()
		assertEquals("prices_2026-10-28.json.apack", file.name)
		val bytes = file.readBytes()
		assertTrue(ApexPack.isApexPack(bytes, 0, bytes.size))
		assertEquals(PriceHistory.encode(PriceHistory.DayFile(mutableMapOf("A" to mutableListOf(PriceHistory.Point(5, 1.0))))), PriceHistory.unpack(bytes))
	}

	@Test
	fun gzipDayFilesStillReadAndMoveIntoThePack() {
		val old = PriceHistory.DayFile(mutableMapOf("A" to mutableListOf(PriceHistory.Point(1, 0.5))))
		Files.write(folder.resolve("prices_2026-10-27.json.gz"), gz(PriceHistory.encode(old)))
		Files.write(folder.resolve("prices_2026-10-28.json.gz"), gz(PriceHistory.encode(PriceHistory.DayFile(mutableMapOf("A" to mutableListOf(PriceHistory.Point(2, 0.75)))))))
		assertEquals(listOf(1L, 2L), PriceHistory.readAll(folder, "A").map { it.t })
		PriceHistory.append(folder, mapOf("A" to PriceHistory.Point(3, 1.0)), Instant.parse("2026-10-28T02:00:00Z"), 7)
		assertEquals(listOf("prices_2026-10-27.json.gz", "prices_2026-10-28.json.apack"), folder.listDirectoryEntries().map { it.name }.sorted())
		assertEquals(listOf(1L, 2L, 3L), PriceHistory.readAll(folder, "A").map { it.t })
		assertEquals(PriceHistory.encode(old), PriceHistory.unpack(gz(PriceHistory.encode(old))))
	}
}
