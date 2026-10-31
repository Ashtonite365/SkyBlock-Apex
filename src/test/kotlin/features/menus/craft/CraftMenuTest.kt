// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.craft

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.craft.CraftMenu
import dev.ashtonite365.apex.features.menus.craft.CraftMenuSettings
import dev.ashtonite365.apex.features.menus.craft.CraftParser
import dev.ashtonite365.apex.features.menus.craft.CraftSlotMap
import dev.ashtonite365.apex.features.menus.craft.CraftSplit
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.features.menus.craft.Supercraft
import dev.ashtonite365.apex.features.menus.support.SlotText

class CraftMenuTest {
	private val real = RealDumpFixtures.load("craft-table-empty")

	@Test
	fun realEmptyTable() {
		assertEquals("Craft Item", real.title)
		val m = CraftParser.parse(real.title, real.slots)!!
		assertTrue(m.grid.values.all { it == null })
		assertEquals("Recipe Required", m.output?.name)
		assertEquals(listOf(16, 25, 34), m.quickCraft.map { it.slot })
		assertTrue(m.quickCraft.all { it.name == "Quick Crafting Slot" })
		assertEquals(listOf(49), m.others.map { it.slot })
	}

	@Test
	fun minionTablesDeferToH2() {
		fun h2(slots: List<SlotText>) = slots.filter { it.name.isNotEmpty() }.map { dev.ashtonite365.apex.features.menus.minion.CraftSlot(it.slot, it.name, null) }
		assertFalse(CraftSplit.isMinionTable(h2(real.slots)))
		val withMinion = real.slots.map { if (it.slot == 23) SlotText(23, "Cobblestone Minion II") else it }
		assertTrue(CraftSplit.isMinionTable(h2(withMinion)))
		val centre = real.slots + SlotText(20, "Quartz Minion I")
		assertTrue(CraftSplit.isMinionTable(h2(centre)))
		val quickOnly = real.slots.map { if (it.slot == 16) SlotText(16, "Cobblestone Minion II") else it }
		assertFalse(CraftSplit.isMinionTable(h2(quickOnly)))
		assertTrue(CraftSplit.isMinionTable(listOf(dev.ashtonite365.apex.features.menus.minion.CraftSlot(23, "Weird", "COBBLESTONE_GENERATOR_2"))))
		val plain = real.slots.map { if (it.slot == 23) SlotText(23, "Oak Planks") else it } + SlotText(10, "Oak Log")
		assertFalse(CraftSplit.isMinionTable(h2(plain)))
	}
	private fun window(vararg filled: Pair<Int, String>): List<SlotText> =
		(0 until 54).map { i -> filled.firstOrNull { it.first == i }?.let { SlotText(i, it.second) } ?: if (i in CraftSlotMap.GRID) null else SlotText(i, "") }
			.filterNotNull()

	@Test
	fun verifiedByTheRealDump() {
		assertTrue(CraftSlotMap.VERIFIED)
		assertTrue(CraftMenu.enabledFor(apex = true, verified = CraftSlotMap.VERIFIED))
		assertFalse(CraftMenu.enabledFor(apex = true, verified = false))
		assertTrue(CraftMenu.enabledFor(apex = true, verified = true))
		assertFalse(CraftMenu.enabledFor(apex = false, verified = true))
	}

	@Test
	fun parsesAndKeepsEveryButton() {
		val m = CraftParser.parse("Craft Item", window(10 to "Oak Log", 49 to "Close", 50 to "Recipe Book", 23 to "Oak Planks"))!!
		assertEquals("Oak Log", m.grid[10]?.name); assertNull(m.grid[11])
		assertEquals("Oak Planks", m.output?.name)
		assertEquals(listOf(49, 50), m.others.map { it.slot })
		assertNull(CraftParser.parse("Craft Items", window()))
		assertNull(CraftParser.parse("Craft Item", window(), chestSlots = 45))
		assertNull(CraftParser.parse("Craft Item", window() + SlotText(20, "")))
		assertNotNull(CraftParser.parse("Craft Item", window()))
	}

	@Test
	fun cellsAndDelay() {
		assertEquals(listOf(null, "STICK" to 2), Supercraft.cells(listOf("CORE_SENTINEL_EMPTY" to 0.0, "STICK" to 2.0)))
	}

	@Test
	fun noSupercraftChain() {

		CraftMenuSettings.explicitDefaultLoad()
		assertNull(CraftMenuSettings.allOptions["supercraft-chain"])
	}

	@Test
	fun supercraftButtonForwardsExactlyOneClickToHypixelsItem() {

		val with = CraftParser.parse("Craft Item", window(49 to "Close", 32 to "Supercraft"))!!
		assertEquals(32, Supercraft.slot(with))
		val sent = mutableListOf<Int>()
		assertTrue(Supercraft.press(with) { sent += it })
		assertEquals(listOf(32), sent)
		val without = CraftParser.parse("Craft Item", window(49 to "Close", 50 to "Recipe Book"))!!
		assertNull(Supercraft.slot(without))
		assertFalse(Supercraft.press(without) { sent += it })
		assertEquals(listOf(32), sent)
	}

	@Test
	fun gridCellsAreRealSlotsSoStacksSplit() {
		CraftSlotMap.GRID.forEach { assertTrue(CraftSlotMap.isRealGridCell(it)) }
		assertFalse(CraftSlotMap.isRealGridCell(CraftSlotMap.OUTPUT))
		assertFalse(CraftSlotMap.isRealGridCell(CraftSlotMap.CLOSE))
		CraftSlotMap.QUICK_CRAFT.forEach { assertFalse(CraftSlotMap.isRealGridCell(it)) }
	}
}
