// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.guides

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.guides.*
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class GuidesTest {
	private fun block(n: String) = RealDumpFixtures.load(n).let { BlockGuideParser.parse(it.title, it.slots) }
	private fun ench(n: String) = RealDumpFixtures.load(n).let { EnchantGuideParser.parse(it.title, it.slots) }!!

	@Test fun blockGuideMain() {
		val p = block("blockguide-main") as BlockGuidePage.Main
		assertEquals(listOf("Ores", "Blocks", "Dwarven Metals", "Gemstones", "Miscellaneous"), p.categories.map { it.name })
		assertEquals(listOf("Gemstone Fortune", "Mining Fortune", "Gemstone Spread", "Pristine"), p.categories[3].affectedBy)
		assertTrue(p.categories.last().affectedBy.isEmpty())
		assertEquals(listOf("Go Back", "Close"), p.nav.map { it.name })
	}

	@Test fun blockPages() {
		val ores = block("blockguide-ores") as BlockGuidePage.Category
		assertEquals(listOf("Ore Fortune", "Mining Fortune", "Mining Spread"), ores.affectedBy)
		assertEquals(17, ores.blocks.size)
		assertNull(ores.blocks.first { it.name == "Coal Ore" }.breakingPower)
		val sulphur = ores.blocks.first { it.name == "Sulphur Ore" }
		assertEquals(8, sulphur.breakingPower); assertEquals(500, sulphur.strength)
		assertEquals(listOf("No Breaking Power listed", "Breaking Power 3", "Breaking Power 8"), ores.groups().map { it.first })

		val dw = block("blockguide-dwarven") as BlockGuidePage.Category
		assertEquals(listOf(4, 5, 9, 9, 9), dw.blocks.map { it.breakingPower })
		assertEquals(2_000, dw.blocks.first { it.name == "Titanium Ore" }.strength)
		assertEquals("Dwarven Metal", dw.blocks.first().kind)

		val gem = block("blockguide-gemstones") as BlockGuidePage.Category
		assertEquals(12, gem.blocks.size); assertEquals(listOf(6, 7, 8, 9), gem.groups().map { it.second.first().breakingPower })
		assertEquals(12, (block("blockguide-blocks") as BlockGuidePage.Category).blocks.size)
		val misc = block("blockguide-misc") as BlockGuidePage.Category
		assertEquals(listOf("Anthill"), misc.blocks.map { it.name }); assertTrue(misc.affectedBy.isEmpty())
	}

	@Test fun unrelatedChestsNotTakenOver() {
		val other = listOf(SlotText(4, "Blocks", listOf("Building materials")), SlotText(10, "Stone", listOf("COMMON")))
		assertNull(BlockGuideParser.parse("Blocks", other))
		assertNull(BlockGuideParser.parse("Ores", listOf(SlotText(10, "Coal Ore", listOf("Ore")))))
		for (t in listOf("Gemstone Grinder", "Ores Shop", "Your Gemstones", "Bazaar ➜ Mining", "Enchant Item", "Enchanted Book"))
			assertFalse(BlockGuideParser.TITLE.matches(t) || EnchantGuideParser.TITLE.containsMatchIn(t), t)
	}

	@Test fun enchantGuidePages() {
		val p1 = ench("enchguide-1")
		assertEquals(1 to 6, p1.page); assertEquals(28, p1.enchants.size)
		assertEquals("None", p1.filter); assertNotNull(p1.searchSlot); assertNotNull(p1.filterSlot)
		assertTrue(p1.nav.any { it.name == "Next Page" } && p1.nav.none { it.name == "Previous Page" })
		val big = p1.enchants.first { it.base == "Big Brain" }
		assertEquals(5, big.maxLevel); assertEquals(21, big.requiredEnchanting)
		assertEquals(listOf("Small Brain"), big.conflicts); assertEquals(listOf("Helmet"), big.appliedTo)
		assertEquals(listOf("Dark Auction (III-V)", "Bazaar (III-V)"), big.sources)
		val aqua = p1.enchants.first { it.base == "Aqua Affinity" }
		assertEquals(listOf("Enchantment Table (I)"), aqua.sources)
		assertEquals(10, p1.enchants.first { it.base == "Absorb" }.maxLevel)
		assertTrue(p1.enchants.none { it.ultimate })

		val p2 = ench("enchguide-2")
		assertEquals(2 to 6, p2.page); assertEquals(28, p2.enchants.size)
		assertTrue(p2.nav.any { it.name == "Previous Page" } && p2.nav.any { it.name == "Next Page" })

		val p6 = ench("enchguide-6")
		assertEquals(6 to 6, p6.page); assertEquals(13, p6.enchants.size)
		assertTrue(p6.enchants.first { it.base == "Legion" }.ultimate)
		assertEquals(34, p6.enchants.first { it.base == "Legion" }.requiredEnchanting)
		assertEquals("One For All", p6.enchants.first { it.maxLevel == 1 }.base)
		assertTrue(p6.nav.none { it.name == "Next Page" })
		assertEquals(listOf("Enchantments", "Ultimate Enchantments").filter { g -> p6.groups().any { it.first == g } }, p6.groups().map { it.first })
	}

	@Test fun differingMaxLevels() {
		val p1 = ench("enchguide-1")
		assertEquals(mapOf("Absorb" to 10, "Angler" to 6, "Aqua Affinity" to 1, "Bane of Arthropods" to 7, "Big Brain" to 5),
			listOf("Absorb", "Angler", "Aqua Affinity", "Bane of Arthropods", "Big Brain").associateWith { b -> p1.enchants.first { it.base == b }.maxLevel })
		val syn = listOf(SlotText(10, "Made Up XIV", listOf("x")), SlotText(11, "Other 3", listOf("y")), SlotText(12, "No Level", listOf("z")),
			SlotText(4, "⚑ Enchantment Guide", listOf("View")))
		val p = EnchantGuideParser.parse("(1/1) Enchantment Guide", syn)!!
		assertEquals(listOf(14, 3, null), p.enchants.map { it.maxLevel })
	}

	@Test fun titles() {
		for (t in listOf("(1/6) Enchantment Guide", "(6/6) Enchantment Guide", "Enchantments Guide")) assertTrue(EnchantGuideParser.TITLE.containsMatchIn(t), t)
		for (t in listOf("Handy Block Guide", "Ores", "Dwarven Metals")) assertTrue(BlockGuideParser.TITLE.matches(t), t)
		assertNull(EnchantGuideParser.parse("(1/6) Enchantment Guide", emptyList()))
	}

	@Test fun enchantGuideMiddlePages() {
		val p3 = ench("enchguide-3"); val p4 = ench("enchguide-4"); val p5 = ench("enchguide-5")
		assertEquals(listOf(3 to 6, 4 to 6, 5 to 6), listOf(p3, p4, p5).map { it.page })
		for (p in listOf(p3, p4, p5)) {
			assertEquals(28, p.enchants.size)
			assertTrue(p.nav.any { it.name == "Previous Page" } && p.nav.any { it.name == "Next Page" })
			assertNotNull(p.searchSlot); assertNotNull(p.filterSlot)
			assertTrue(p.enchants.all { it.maxLevel != null && it.appliedTo.isNotEmpty() && it.sources.isNotEmpty() }, p.page.toString())
		}
		assertEquals("Looting", p3.enchants.first().base); assertEquals("Respite", p3.enchants.last().base)
		assertEquals(mapOf("Piercing" to 1, "Punch" to 2, "Respiration" to 4, "Mana Steal" to 3, "Luck of the Sea" to 7),
			listOf("Piercing", "Punch", "Respiration", "Mana Steal", "Luck of the Sea").associateWith { b -> p3.enchants.first { it.base == b }.maxLevel })
		assertTrue(p3.enchants.none { it.ultimate })

		val sharp = p4.enchants.first { it.base == "Sharpness" }
		assertEquals(7, sharp.maxLevel); assertNull(sharp.requiredEnchanting)
		assertEquals(listOf("Sword", "Longsword", "Gauntlet"), sharp.appliedTo)
		assertEquals("Scorpius (VII)", sharp.sources.last())
		val silk = p4.enchants.first { it.base == "Silk Touch" }
		assertEquals(listOf("Enchantment Table (I)"), silk.sources); assertEquals(listOf("Fortune", "Smelting Touch"), silk.conflicts)
		val small = p4.enchants.first { it.base == "Small Brain" }
		assertEquals(21, small.requiredEnchanting); assertEquals(listOf("Big Brain"), small.conflicts)
		assertEquals(10, p4.enchants.first { it.base == "Strong Vitality" }.maxLevel)
		assertEquals("Triple-Strike", p4.enchants.first { it.name == "Triple-Strike V" }.base)
		assertEquals(6, p4.enchants.first { it.base == "Scavenger" }.maxLevel)
		assertTrue(p4.enchants.none { it.ultimate })

		val ult = p5.enchants.filter { it.ultimate }.map { it.base }
		assertEquals("Bank", ult.first()); assertEquals("Last Stand", ult.last())
		assertTrue("Bobbin' Time" in ult && "Habanero Tactics" in ult)
		assertEquals(15, p5.enchants.count { !it.ultimate }); assertEquals("Woodsplitter", p5.enchants.filter { !it.ultimate }.last().base)
		assertEquals(16, p5.enchants.first { it.base == "Bank" }.requiredEnchanting)
		assertEquals(3, p5.enchants.first { it.base == "Flowstate" }.maxLevel)
		assertEquals(listOf("Enchantments", "Ultimate Enchantments"), p5.groups().map { it.first })
	}
}
