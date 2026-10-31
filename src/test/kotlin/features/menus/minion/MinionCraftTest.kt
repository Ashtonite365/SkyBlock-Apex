package dev.ashtonite365.apex.test.features.menus.minion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.minion.CraftContext
import dev.ashtonite365.apex.features.menus.minion.CraftSlot
import dev.ashtonite365.apex.features.menus.minion.MinionCraftParser as P
import dev.ashtonite365.apex.features.menus.minion.MinionCraftSlots
import dev.ashtonite365.apex.features.menus.minion.MinionRef
import dev.ashtonite365.apex.features.menus.minion.MinionScreenKind
import dev.ashtonite365.apex.features.menus.support.SlotDumpGate
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class MinionCraftTest {
	private fun load(n: String) = RealDumpFixtures.load(n).let { d -> d.title to d.slots.map { CraftSlot(it.slot, it.name.trim(), null) } }
	private fun lore(n: String) = RealDumpFixtures.load(n).slots.associate { it.slot to it.lore }

	@Test
	fun tierRecipeViewer() {
		val (t, s) = load("minioncraft-tier7")
		assertTrue(P.TITLE_HINT.matches(t))
		assertEquals(MinionScreenKind.TIER_RECIPE, P.classify(s))
		val m = P.parse(s)!!
		assertEquals(CraftContext.MINION_UPGRADE, m.context)
		assertEquals(MinionRef("Quartz", 6), m.centre); assertEquals(MinionRef("Quartz", 7), m.result)
		assertEquals(25, m.resultSlot)
		assertEquals(mapOf("Enchanted Nether Quartz" to 8), m.placed)
		assertEquals(listOf(P.Requirement("Enchanted Nether Quartz", 0, 64), P.Requirement("Quartz Minion VI", 0, 1)), P.requirements(s, lore("minioncraft-tier7")))
		assertFalse(P.isMinionCraftTable(s))
	}

	@Test
	fun tierOneIsACraftNotAnUpgrade() {
		val (_, s) = load("minioncraft-tier1")
		val m = P.parse(s)!!
		assertEquals(CraftContext.MINION_CRAFT, m.context); assertNull(m.centre); assertEquals(MinionRef("Quartz", 1), m.result)
		val req = P.requirements(s, lore("minioncraft-tier1"))
		assertEquals(listOf(false, true), req.map { it.ok })
		assertEquals(mapOf("Wooden Pickaxe" to 1), P.missing(req.associate { it.name to it.need.toInt() }, req.associate { it.name to it.have.toInt() }))
	}

	@Test
	fun recipesListAndMaxTierFromSlots() {
		val (t, s) = load("minioncraft-recipes")
		assertTrue(P.TITLE_HINT.matches(t))
		assertEquals(MinionScreenKind.RECIPES_LIST, P.classify(s))
		assertEquals((1..11).toList(), P.tiers(s).map { it.second.tier })
		assertEquals(listOf(11, 12, 13, 14, 15, 21, 22, 23, 30, 31, 32), P.tiers(s).map { it.first })
		assertEquals(11, P.maxTierFromList(s))
	}

	@Test
	fun craftingTableSplitWithH1() {
		val (t, s) = load("minioncraft-table-empty")
		assertEquals("Craft Item", t)
		assertFalse(P.isMinionCraftTable(s))
		assertEquals(MinionScreenKind.OTHER, P.classify(s))
		val withMinion = s.filter { it.index != 23 } + CraftSlot(20, "Cobblestone Minion III", "COBBLESTONE_GENERATOR_3") +
			CraftSlot(23, "Cobblestone Minion IV", "COBBLESTONE_GENERATOR_4")
		assertTrue(P.isMinionCraftTable(withMinion))
		assertEquals(23, P.parse(withMinion)!!.resultSlot)

		assertFalse(P.isMinionCraftTable(s + CraftSlot(25, "Cobblestone Minion I", "COBBLESTONE_GENERATOR_1")))

		assertFalse(P.isMinionCraftTable(s.filter { it.index != 23 } + CraftSlot(20, "Stick", "STICK") + CraftSlot(23, "Wooden Pickaxe", "WOOD_PICKAXE")))
	}

	@Test
	fun anyMinionTypeAndLongCutTitles() {
		val snow = listOf(CraftSlot(20, "Enchanted Snow Minion IV", null), CraftSlot(23, "Crafting Table", null),
			CraftSlot(25, "Enchanted Snow Minion V", null), CraftSlot(10, "Enchanted Snow Block", null), CraftSlot(48, "Go Back", null))
		assertEquals(MinionScreenKind.TIER_RECIPE, P.classify(snow))
		assertEquals(MinionRef("Enchanted Snow", 5), P.parse(snow)!!.result)
		assertEquals(MinionRef("COBBLESTONE_GENERATOR", 12), P.minion(CraftSlot(1, "", "COBBLESTONE_GENERATOR_12")))
		for (t in listOf("Cobblestone Minion XII", "Red Mushroom Minion Recipes", "Enchanted Cobblestone Minion Rec", "Very Long Synthetic Minion Name Mi", "Snow Minion R"))
			assertTrue(P.TITLE_HINT.matches(t), t)
		for (t in listOf("Craft Item", "Crafted Minions", "Ideal Layout (Top-down view)", "Minion Chest")) assertFalse(P.TITLE_HINT.matches(t), t)
	}

	@Test
	fun maxTierFromItemData() {
		val max = mapOf("Quartz" to 11, "COBBLESTONE_GENERATOR" to 12)
		assertTrue(P.isMaxTier(MinionRef("Quartz", 11), max)); assertNull(P.nextTier(MinionRef("Quartz", 11), max))
		assertFalse(P.isMaxTier(MinionRef("Quartz", 10), max)); assertEquals(11, P.nextTier(MinionRef("Quartz", 10), max))
		assertTrue(P.isMaxTier(MinionRef("COBBLESTONE_GENERATOR", 12), max))
		assertFalse(P.isMaxTier(MinionRef("Unknown", 99), max))
	}

	@Test
	fun minionWindowStaysH1s() {
		val (_, s) = load("minion-main")
		assertEquals(MinionScreenKind.MINION_WINDOW, P.classify(s))
	}

	@Test
	fun slotDumpForReusedContainerIds() {
		val g = SlotDumpGate(); val S = SlotDumpGate.STABLE_MS
		assertFalse(g.offer(12, 1, 5, 0)); assertTrue(g.offer(12, 1, 5, S + 1))

		g.opened(12)
		assertFalse(g.offer(12, 2, 9, 1000)); assertTrue(g.offer(12, 2, 9, 1000 + S + 1))
		assertFalse(g.offer(12, 2, 9, 5000))
	}
}

class MinionCraftPressTest {
	@Test
	fun notEnoughItemsReply() {

		assertEquals(P.CraftResult(512, 0, 512), P.craftResult("You don't have enough items to craft that tier! You need 512 more. (0/512)"))
		assertEquals(P.CraftResult(1200, 100, 1300), P.craftResult("You don't have enough items to craft that tier! You need 1,200 more. (100/1,300)"))
		assertNull(P.craftResult("You don't have enough items!"))
	}

	@Test
	fun upgradeViewIsClaimedByTheCraftScreenNotTheMinionMenu() {
		val d = RealDumpFixtures.load("minioncraft-tier10")
		assertEquals("Quartz Minion X", d.title)

		assertNull(dev.ashtonite365.apex.features.menus.minion.MinionParser.parse(d.title, d.slots))
		val cs = d.slots.map { CraftSlot(it.slot, it.name.trim(), null) }
		assertEquals(MinionScreenKind.TIER_RECIPE, P.classify(cs))
		assertEquals(MinionRef("Quartz", 10), P.parse(cs)!!.result)
	}
}
