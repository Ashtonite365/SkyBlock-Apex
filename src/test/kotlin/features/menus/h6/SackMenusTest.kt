package dev.ashtonite365.apex.test.features.menus.h6

import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.SackDirectory
import dev.ashtonite365.apex.features.menus.h6.SackPage
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SackMenusTest {
	companion object {
		val SACKS = listOf("enchanted-combat", "combat", "enchanted-mining", "mining", "enchanted-husbandry", "husbandry", "spooky", "fishing",
			"winter", "enchanted-foraging", "foraging", "enchanted-agronomy", "agronomy", "dragons", "gemstones", "slayer").map { "sacks-$it-sack" }
	}

	@AfterEach fun reset() = SackDirectory.resetForTests()

	private fun live(name: String) = RealDumpFixtures.load(name).let { it.title to it.slots.filter { s -> s.cleanName.isNotEmpty() } }
	private fun parse(name: String) = live(name).let { (t, s) -> BagsParser.parse(t, s) }
	private fun HubModel.chip(l: String) = chips.firstOrNull { it.first == l }?.second
	private fun overview() = parse("sacks-sack-of-sacks")!!

	@Test fun storedLine() {
		assertEquals(1213L to 20200L, SackPage.stored(listOf("Foraging Sack", "", "Stored: 1,213/20.2k")))
		assertEquals(100755L to 2600000L, SackPage.stored(listOf("Stored: 100,755/2.6M")))
		assertNull(SackPage.stored(listOf("Capacity: 20,160 of each item")))
	}

	@Test fun everySackWindowParsesFromLoreOnly() {
		overview()
		for (f in SACKS) {
			val (t, slots) = live(f)
			val m = BagsParser.parse(t, slots); assertNotNull(m, f); m!!
			val expected = slots.count { SackPage.stored(it.lore) != null }
			assertTrue(expected > 0, f)
			assertEquals(expected, m.allTiles.size, "$f: one tile per Stored item")
			val (have, all) = m.chip("Items")!!.split('/').map { it.toInt() }
			assertEquals(expected, all, f)
			assertEquals(slots.count { (SackPage.stored(it.lore)?.first ?: 0) > 0 }, have, f)
			assertTrue(m.allTiles.all { it.button == 0 && !it.delayed }, "$f: items are normal clicks")
			if (slots.any { it.cleanName == "Pickup All" }) assertTrue(m.nav.any { it.first == "Pickup All" }, f)
			assertNotNull(m.chip("Sack"), f)
		}
	}

	@Test fun sackNameAndTierComeFromTheOverview() {
		assertEquals("Combat Sack", parse("sacks-combat-sack")!!.chip("Sack"), "overview unseen: window title only")
		overview()
		val c = parse("sacks-combat-sack")!!
		assertEquals("Large Combat Sack", c.chip("Sack")); assertEquals("Large", c.chip("Tier"))
		val ec = parse("sacks-enchanted-combat-sack")!!
		assertEquals("Large Enchanted Combat Sack", ec.chip("Sack")); assertEquals("Large", ec.chip("Tier"))

		assertEquals("Medium Dragon Sack", parse("sacks-dragons-sack")!!.chip("Sack"))
		assertEquals("Medium", parse("sacks-dragons-sack")!!.chip("Tier"))
		assertEquals("Large Gemstone Sack", parse("sacks-gemstones-sack")!!.chip("Sack"))
		assertNull(parse("sacks-spooky-sack")!!.chip("Tier"), "no tier word in 'Spooky Sack'")
	}

	@Test fun clickedSackCountsWithoutOverview() {
		SackDirectory.clicked("Large Enchanted Mining Sack")
		assertEquals("Large Enchanted Mining Sack", parse("sacks-enchanted-mining-sack")!!.chip("Sack"))
		assertEquals("Mining Sack", parse("sacks-mining-sack")!!.chip("Sack"), "a click on another sack never claims this window")
	}

	@Test fun countsCapacityAndGemBreakdown() {
		overview()
		val g = parse("sacks-gemstones-sack")!!
		val jade = g.allTiles.first { HubText.clean(listOf(it.name)).first() == "Jade Gemstones" }
		assertTrue(jade.info[0].startsWith("100,755/"), jade.info[0])
		assertEquals("Rough: 26,915 (26,915)", jade.info[1])
		assertTrue(g.nav.any { it.first == "Filter: None" })

		assertEquals("%,d".format(java.util.Locale.ROOT, live("sacks-gemstones-sack").second.sumOf { SackPage.stored(it.lore)?.first ?: 0L }), g.chip("Stored"))
		val c = parse("sacks-combat-sack")!!
		assertEquals(live("sacks-combat-sack").second.first { it.cleanName == "Pickup All" }.let { HubText.kv(it.lore)["total items"] }, c.chip("Stored"))
	}

	@Test fun ownedSacksOnlyFromTheScreen() {
		val o = overview()
		val names = o.allTiles.map { HubText.clean(listOf(it.name)).first() }
		assertEquals(16, names.size)
		assertEquals(names, SackDirectory.owned)
		assertEquals(1, o.allTiles.first().button, "sacks open with right click (lore)")
	}

	@Test fun emptyPotionBag() {
		val m = parse("sacks-potion-bag")!!
		assertTrue(m.allTiles.isEmpty())
		assertEquals("Empty", m.emptyText)
		assertNull(m.locked)
		assertEquals("0", m.chip("Items"))
		assertTrue(m.nav.any { it.first.startsWith("Back") })
	}

	@Test fun noSackOrItemConstantsInSackCode() {
		val src = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
			.map { File(it, "src/main/kotlin/features/menus/h6/Bags.kt") }.first { it.exists() }.readText()
		val literals = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(src).map { it.groupValues[1] }.toList()
		val names = mutableSetOf<String>()
		for (f in SACKS + "sacks-sack-of-sacks") RealDumpFixtures.load(f).let { d ->
			names += d.title
			d.slots.filter { SackPage.stored(it.lore) != null || f.endsWith("sacks-sack-of-sacks") && !HubText.isNav(it) }.forEach { names += it.cleanName }
		}
		names.removeAll { it.isEmpty() || it == "Sack of Sacks" || it == "Insert inventory" }
		val tiers = names.filter { it.endsWith(" Sack") }.map { it.substringBefore(' ') }.toSet() - setOf("Spooky", "Winter")
		val hits = literals.filter { lit -> names.any { lit.contains(it) } || tiers.any { Regex("\\b$it\\b").containsMatchIn(lit) } }
		assertEquals(emptyList<String>(), hits, "hard-coded sack/item names in Bags.kt")
		assertFalse(Regex("(listOf|setOf|mapOf|arrayOf)\\(\\s*\"[^\"]*\",\\s*\"[^\"]*\",\\s*\"").containsMatchIn(src), "string list constant in Bags.kt")
	}
}
