// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.ahmiss

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.ah.AhGrid
import dev.ashtonite365.apex.features.menus.ah.AhModel
import dev.ashtonite365.apex.features.menus.ah.AhSlot
import dev.ashtonite365.apex.features.museum.AhSeenPrices
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.features.museum.MuseumHelper.Missing
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.util.ItemId

class AhMissTest {
	private fun ah(name: String) = RealDumpFixtures.load(name).let { d ->
		d.title to d.slots.map { AhSlot(it.slot, it.name.trim(), it.lore, it.name.isNotBlank(), it.name.isBlank()) }
	}

	@Test fun dungeonOrbCheapestCardIsReachable() {
		val (t, s) = ah("ah-dungeon-orb-p2")
		val p = AhModel.parse(t, s)!!
		val prices = p.cards.map { it.listing!!.price!! }
		assertEquals(80_000.0, prices.last())
		assertEquals(80_000.0, prices.min())
		val cols = 3; val fit = 2
		val old = p.cards.take(fit * cols).map { it.listing!!.price!! }
		assertEquals(90_000.0, old.min())
		assertTrue(AhGrid.hiddenBelow(p.cards.size, cols, fit, 0) > 0)
		val end = AhGrid.maxScroll(p.cards.size, cols, fit)
		val seen = (0..end).flatMap { AhGrid.visible(p.cards.size, cols, fit, it).toList() }.toSet()
		assertEquals(p.cards.indices.toSet(), seen)
		assertEquals(0, AhGrid.hiddenBelow(p.cards.size, cols, fit, end))
	}

	@Test fun gridMaths() {
		assertEquals(0 until 16, AhGrid.visible(24, 4, 4, 0))
		assertEquals(8, AhGrid.hiddenBelow(24, 4, 4, 0))
		assertEquals(8 until 24, AhGrid.visible(24, 4, 4, 99))
		assertEquals(0 until 5, AhGrid.visible(5, 4, 4, 3))
		assertEquals(4, AhGrid.rowsFit(172, 40))
	}

	@Test fun ownFiftyCoinNetParsedAndReachable() {
		val (t, s) = ah("ah-dungeon-orb-p1")
		val net = AhSlot(43, "Basic Fishing Net", listOf("Tools", "", "-----------------", "Seller: [MVP+] Ashtonite365",
			"Starting bid: 50 coins", "", "Ends in: 2d", "", "This is your own auction!"), true, false)
		val p = AhModel.parse("Auctions: \"Basic Fishing Net\"", s.filter { it.index != 43 } + net)!!
		val i = p.cards.indexOfFirst { it.slot == 43 }
		assertTrue(i >= 0, "own auction must not be filtered")
		assertEquals(50.0, p.cards[i].listing!!.price)
		assertFalse(p.cards[i].listing!!.isBin)
		assertEquals(i, p.cards.lastIndex)
		val cols = 4; val fit = 4
		assertFalse(i in AhGrid.visible(p.cards.size, cols, fit, 0))
		assertTrue(i in AhGrid.visible(p.cards.size, cols, fit, AhGrid.maxScroll(p.cards.size, cols, fit)))
	}

	@Test fun searchTermFromSearchButtonNotTitle() {
		val (_, s) = ah("ah-dungeon-orb-p1")
		val search = AhSlot(48, "Search", listOf("Find items by name, type, lore, or", "enchants.", "", "Filtered: Seriously Damaged Axe", "", "Right-Click to clear!"), true, false)
		val cut = "Auctions: \"Seriously Damaged Axe"
		val p = AhModel.parse(cut, s.filter { it.index != 48 } + search)!!
		assertEquals("Seriously Damaged Axe", AhModel.searchTerm(p))
		val (t1, s1) = ah("ah-dungeon-orb-p1")
		assertEquals("Dungeon Orb", AhModel.searchTerm(AhModel.parse(t1, s1)!!))
	}

	@Test fun seenListingsAreLiveAhPricesByExactId() {
		AhSeenPrices.clear()
		val (t, s) = ah("ah-dungeon-orb-p2")
		val p = AhModel.parse(t, s)!!
		AhSeenPrices.record(p.cards.map { "DUNGEON_STONE" to it.listing } + ("RUSTY_X" to p.cards.last().listing!!.copy(price = 1.0)), 1_000L)
		assertEquals(80_000.0, AhSeenPrices.bin("DUNGEON_STONE", 2_000L))
		assertNull(AhSeenPrices.bin("DUNGEON_STONE", 1_000L + AhSeenPrices.FRESH_MS + 1))
		assertNull(AhSeenPrices.bin("DUNGEON_ORB", 2_000L))
		assertEquals(80_000.0, MuseumHelper.liveBin(99_999.0, 80_000.0))
		assertEquals(80_000.0, MuseumHelper.liveBin(null, 80_000.0))
		assertNull(MuseumHelper.liveBin(null, null))
		AhSeenPrices.clear()
	}

	@Test fun noLivePriceText() {
		val r = Missing("Thing", listOf(ItemId("THING")), "x", 0, { null }, { null }, { null }, { true }, { null }, { false })
		assertEquals("§8no live price", MuseumHelper.priceText(r) { it.toString() })
	}

	@Test fun museumPricingHasNoConstantPrices() {
		val files = listOf("features/museum/MuseumHelper.kt", "features/museum/AhSeenPrices.kt",
			"features/museum/MuseumAhTarget.kt", "repo/LiveAuctionPrices.kt").map { File(srcRoot(), it) }
		val timeOk = setOf("60_000L", "1000", "3600")
		for (f in files) {
			assertTrue(f.exists(), f.path)
			val code = f.readText().replace(Regex("/\\*[\\s\\S]*?\\*/"), "").lines().joinToString("\n") { it.substringBefore("//") }
				.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "\"\"")
			val bad = Regex("(?<![\\w.])(?!0x)\\d[\\d_]*(?:\\.\\d+)?[LDdFf]?(?![\\w.])").findAll(code).map { it.value }
				.filter { it !in timeOk && (it.trimEnd('L', 'D', 'd', 'F', 'f').replace("_", "").toDoubleOrNull() ?: 0.0) >= 1000 }.toList()
			assertEquals(emptyList<String>(), bad, "constant price-like literal in ${f.path}")
			assertFalse(Regex("\"[A-Z][A-Z0-9_]+\"\\s+to\\s+\\d").containsMatchIn(code), "id -> number table in ${f.path}")
			for (bz in listOf("getPriceOfItem", "quickStatus", "buyPrice", "sellPrice", "avg1dlowestBin", "avg3dlowestBin", "avg7dlowestBin", "HypixelStaticData.lowestBin"))
				assertFalse(bz in code && !(f.name == "LiveAuctionPrices.kt" && bz.startsWith("avg")), "$bz in ${f.path}")
		}
	}

	private fun srcRoot(): File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
		.map { File(it, "src/main/kotlin") }.first { File(it, "features/museum/MuseumHelper.kt").exists() }

	@Test fun constantScanCatchesATable() {
		val sample = "val FALLBACK = mapOf(\"DUNGEON_STONE\" to 90_000.0)"
		assertTrue(Regex("\"[A-Z][A-Z0-9_]+\"\\s+to\\s+\\d").containsMatchIn(sample))
	}
}
