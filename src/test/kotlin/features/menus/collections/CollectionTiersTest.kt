// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CollectionTiersTest {
	private fun parse(name: String) = RealDumpFixtures.load(name).let { CollectionsParser.parse(it.title, it.slots) }

	@Test fun realCoalItemPage() {
		val p = parse("coal-collection") as CollPage.Item
		assertEquals("Coal", p.item)
		assertEquals((1..10).toList(), p.tiers.map { it.tier })
		assertEquals(10, p.maxTier); assertEquals(10, p.unlockedTiers); assertTrue(p.maxed); assertNull(p.next)
		assertTrue(p.tiers.all { it.state == TileState.UNLOCKED })
		val iv = p.tiers.first { it.tier == 4 }
		assertEquals(453_383.0, iv.progress!!.current); assertEquals(1_000.0, iv.progress!!.max); assertEquals(1.0, iv.progress!!.fraction)
		assertEquals(listOf("[Lvl 1] Wither Skeleton Recipe", "+4 SkyBlock XP"), iv.details)
		assertTrue(p.header!!.extra.any { it.startsWith("Total Collected") })
		assertEquals(listOf("Go Back", "Close"), p.nav.map { it.name })
	}

	@Test fun realMiningCategory() {
		val p = parse("mining-collections") as CollPage.Category
		assertEquals("Mining", p.category)
		assertEquals(22.0, p.header!!.unlocked!!.current); assertEquals(25.0, p.header!!.unlocked!!.max)
		val by = p.items.associateBy { it.base }

		assertEquals(mapOf("Coal" to 10, "Diamond" to 9, "Gemstone" to 11, "Iron Ingot" to 12, "Hard Stone" to 7),
			listOf("Coal", "Diamond", "Gemstone", "Iron Ingot", "Hard Stone").associateWith { by.getValue(it).tier })
		listOf("Coal", "Diamond", "Gemstone", "Iron Ingot", "Hard Stone").forEach { assertEquals(TileState.MAXED, by.getValue(it).state, it) }
		assertEquals(453_383.0, by.getValue("Coal").total)
		val em = by.getValue("Emerald")
		assertEquals(TileState.IN_PROGRESS, em.state); assertEquals(7, em.tier)
		assertEquals(31_835.0, em.progress!!.current); assertEquals(50_000.0, em.progress!!.max); assertEquals(63.7, em.progress!!.pct)
		assertEquals(listOf("Emerald Blade Recipe", "+4 SkyBlock XP"), em.details)
		assertEquals(TileState.IN_PROGRESS, by.getValue("Mycelium").state); assertEquals(1, by.getValue("Mycelium").tier)
		assertEquals(listOf("Glacite", "Tungsten", "Umber"), p.items.filter { it.state == TileState.LOCKED }.map { it.name })
		assertEquals(25, p.items.size)
		assertEquals(listOf("In progress", "Maxed", "Locked"), CollectionsParser.groups(p.items).map { it.first })
	}

	private fun tier(slot: Int, item: String, n: Int, done: Boolean) = SlotText(slot, "$item ${roman(n)}",
		listOf("", "Progress: ${if (done) 100 else 40}%", "   ${if (done) 999_999 else 400}/1k", "", "Rewards:", "  +4 SkyBlock XP"))
	private fun roman(n: Int): String {
		val v = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
		var r = n; return buildString { for ((k, s) in v) while (r >= k) { append(s); r -= k } }
	}

	@Test fun syntheticDifferentMaxTiers() {
		for ((max, unlocked) in listOf(5 to 3, 11 to 11, 14 to 13, 25 to 20)) {
			val slots = listOf(SlotText(4, "Thing", listOf("Total Collected: 400"))) +
				(1..max).map { tier(9 + it, "Thing", it, it <= unlocked) } + SlotText(48, "Go Back", listOf("To Mining Collections"))
			val p = CollectionsParser.parse("Thing Collection", slots) as CollPage.Item
			assertEquals((1..max).toList(), p.tiers.map { it.tier }, "max $max")
			assertEquals(max, p.maxTier); assertEquals(unlocked, p.unlockedTiers)
			assertEquals(unlocked == max, p.maxed)
			assertEquals(if (unlocked == max) null else unlocked + 1, p.next?.tier)
		}

		val one = CollectionsParser.parse("Solo Collection", listOf(tier(18, "Solo", 1, false), SlotText(48, "Go Back", listOf("To Mining Collections")))) as CollPage.Item
		assertEquals(1, one.maxTier); assertEquals(1, one.next!!.tier)
	}

	@Test fun romanGeneral() {
		for (n in listOf(1, 4, 9, 12, 14, 19, 24, 40, 49, 90, 400, 1994)) assertEquals(n, LoreParse.roman(roman(n)), roman(n))
		assertEquals("Iron Ingot" to 12, LoreParse.splitTier("Iron Ingot XII"))
		assertEquals("Gemstone" to 11, LoreParse.splitTier("Gemstone XI"))
	}
}
