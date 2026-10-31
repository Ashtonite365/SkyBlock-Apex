// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.minion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.minion.IdealLayoutParser
import dev.ashtonite365.apex.features.menus.minion.IdealLayoutSlotMap
import dev.ashtonite365.apex.features.menus.minion.MinionClicks
import dev.ashtonite365.apex.features.menus.minion.MinionParser
import dev.ashtonite365.apex.features.menus.minion.MinionSlotMap
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class MinionMenuTest {
	private val main = RealDumpFixtures.load("minion-main")
	private val main2 = RealDumpFixtures.load("minion-main-2")
	private val ideal = RealDumpFixtures.load("minion-ideal-layout")

	@Test
	fun parsesRealMainWindow() {
		val m = MinionParser.parse(main.title, main.slots)!!
		assertEquals("Quartz", m.type); assertEquals("XI", m.tierRoman); assertEquals(11, m.tier)
		assertEquals("9.2s", m.timeBetweenActions); assertEquals(960, m.maxStorage); assertEquals(41273L, m.resourcesGenerated)
		assertEquals(mapOf(21 to "Enchanted Nether Quartz", 22 to "Nether Quartz"), m.storage)
		assertEquals(48, m.collectAll.slot); assertEquals(53, m.pickup.slot)
		assertEquals(3, m.idealLayout?.slot); assertEquals(50, m.quickUpgrade?.slot); assertEquals(5, m.nextTier?.slot)
		assertTrue(m.maxTier)
		assertEquals(listOf("Super Compactor 3000"), m.compactors)
		assertTrue(m.others.isEmpty(), "every non-filler slot of the real dump has a role: ${m.others}")
		assertEquals(41275L, MinionParser.parse(main2.title, main2.slots)!!.resourcesGenerated)
	}

	@Test
	fun fiveItemSlotsFilledAndEmpty() {
		val s = MinionParser.parse(main.title, main.slots)!!.itemSlots.associate { it.kind.label to it }
		assertEquals(listOf("Skin", "Fuel", "Automated Shipping", "Upgrade 1", "Upgrade 2"), s.keys.toList())
		assertFalse(s["Skin"]!!.filled); assertFalse(s["Automated Shipping"]!!.filled); assertFalse(s["Upgrade 2"]!!.filled)
		assertTrue(s["Fuel"]!!.filled); assertEquals("Enchanted Lava Bucket", s["Fuel"]!!.name)
		assertEquals("Increases the speed of your minion by 25%. Unlimited Duration!", s["Fuel"]!!.effect)
		assertTrue(s["Upgrade 1"]!!.filled)
		assertEquals(listOf(10, 19, 28, 37, 46), MinionSlotMap.ItemSlot.entries.map { it.slot })
	}

	@Test
	fun unknownButtonsAreKeptGenerically() {
		val extra = main.slots + SlotText(49, "Some New Button", listOf("Click me"))
		val m = MinionParser.parse(main.title, extra.filter { it.slot != 49 || it.name.isNotEmpty() })!!
		assertEquals(listOf(49), m.others.map { it.slot })
	}

	@Test
	fun mismatchFallsBack() {
		assertNull(MinionParser.parse("Quartz Minion XI", main.slots.filter { it.slot != 48 }))
		assertNull(MinionParser.parse("Quartz Minion XI", main.slots.map { if (it.slot == 53) it.copy(name = "Something") else it }))
		assertNull(MinionParser.parse("Quartz Minion XII", main.slots))
		assertNull(MinionParser.parse("Minion Recipes", main.slots))
		assertNull(MinionParser.parse(ideal.title, ideal.slots))
	}

	@Test
	fun titleMatchers() {
		assertTrue(MinionParser.TITLE.matches("Quartz Minion XI"))
		assertTrue(MinionParser.TITLE.matches("Enchanted Snow Minion IV"))
		assertFalse(MinionParser.TITLE.matches("Ideal Layout (Top-down view)"))
		assertFalse(MinionParser.TITLE.matches("Crafted Minions"))
		assertEquals(IdealLayoutSlotMap.TITLE, ideal.title)
		assertFalse(MenuRegistry.denied("Quartz Minion XI"))
		assertFalse(MenuRegistry.denied(IdealLayoutSlotMap.TITLE))
		assertEquals(4, MinionParser.roman("IV")); assertEquals(12, MinionParser.roman("XII"))
	}

	@Test
	fun idealLayoutRealDump() {
		val m = IdealLayoutParser.parse(ideal.title, ideal.slots)!!
		assertEquals(25, m.tiles.size)
		assertEquals("Quartz Minion I", m.minion.name); assertEquals(22, m.minion.slot)
		assertEquals(m.minion, m.tiles[12])
		assertTrue(m.tiles.filterIndexed { i, _ -> i != 12 }.all { it?.name == "Air" && it.lore.first().startsWith("Air should be") })
		assertEquals(48, m.goBack?.slot); assertEquals(49, m.close?.slot)
		assertTrue(m.others.isEmpty())
		assertNull(IdealLayoutParser.parse("Ideal Layout", ideal.slots))
		assertNull(IdealLayoutParser.parse(ideal.title, ideal.slots.filter { it.slot != 22 }))
	}

	@Test
	fun clicksAndInventorySlots() {
		assertEquals(0 to MinionClicks.Kind.PICKUP, MinionClicks.route(0, false))
		assertEquals(1 to MinionClicks.Kind.PICKUP, MinionClicks.route(1, false))
		assertEquals(0 to MinionClicks.Kind.QUICK_MOVE, MinionClicks.route(0, true))
		assertEquals(54, MinionSlotMap.inventorySlot(0, 0)); assertEquals(80, MinionSlotMap.inventorySlot(2, 8))
		assertEquals(81, MinionSlotMap.inventorySlot(3, 0)); assertEquals(89, MinionSlotMap.inventorySlot(3, 8))
		val v = MinionClicks.value(listOf("ENCHANTED_QUARTZ" to 2, "QUARTZ" to 10, null to 1)) { mapOf("ENCHANTED_QUARTZ" to 1500.0, "QUARTZ" to 9.0)[it] }
		assertEquals(3090.0, v.coins); assertEquals(2, v.priced); assertEquals(1, v.unpriced)
	}
}

class MinionSilentTileTest {
	@Test
	fun recipesListTierTilesAreButtons() {
		assertFalse(MinionClicks.tileIsRealItem(dev.ashtonite365.apex.features.menus.minion.MinionScreenKind.RECIPES_LIST))
		assertTrue(MinionClicks.tileIsRealItem(dev.ashtonite365.apex.features.menus.minion.MinionScreenKind.TIER_RECIPE))
	}
}

class MinionCraftResultScopeTest {
	private class Win(val containerId: Int)

	@Test
	fun reusedContainerIdAfterReopenDoesNotShowOldMessage() {
		val s = dev.ashtonite365.apex.features.menus.minion.WindowScoped<String>()
		val first = Win(7)
		s.set(first, "need 3 more")
		assertEquals("need 3 more", s.get(first))
		s.onWindowChange(first)
		assertEquals("need 3 more", s.get(first))
		val reopened = Win(7)
		assertNull(s.get(reopened))
		s.onWindowChange(reopened)
		assertNull(s.get(first))
	}

	@Test
	fun closeClears() {
		val s = dev.ashtonite365.apex.features.menus.minion.WindowScoped<String>()
		val w = Win(3)
		s.set(w, "x"); s.onWindowChange(null)
		assertNull(s.get(w)); assertNull(s.get(null))
	}
}
