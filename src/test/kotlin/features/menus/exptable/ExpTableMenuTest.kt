// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.exptable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.exptable.ExpClick
import dev.ashtonite365.apex.features.menus.exptable.ExpTableClicks
import dev.ashtonite365.apex.features.menus.exptable.ExpTableMenu
import dev.ashtonite365.apex.features.menus.exptable.ExpTableMenuGate
import dev.ashtonite365.apex.features.menus.exptable.ExpTableParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ExpTableMenuTest {
	private val d = RealDumpFixtures.load("exptable-main")
	private val m get() = ExpTableParser.parse(d.title, d.slots)!!

	@Test fun keepsOnlyMeaningfulSlots() {
		assertEquals(listOf(20, 21, 22, 23, 24, 29, 33, 48, 49, 50), m.tiles.map { it.slot })
		assertTrue(m.tiles.none { it.name.isBlank() })
		assertEquals(54 - 10, d.slots.count { ExpTableParser.isFiller(it) })
	}

	@Test fun compactsToSmallerGrid() {
		assertEquals(3, m.rows); assertEquals(5, m.cols)
		val cells = m.tiles.associate { it.name to (it.row to it.col) }
		assertEquals(0 to 2, cells["Superpairs"])
		assertEquals(1 to 0, cells["Chronomatron"])
		assertEquals(1 to 4, cells["Ultrasequencer"])
		assertEquals(2 to 1, cells["Experimentation Table RNG Meter"])
		assertEquals(2 to 2, cells["Close"])
		assertEquals(2 to 3, cells["Experience Bottles"])
	}

	@Test fun relativeOrderAndPositionsPreserved() {
		val t = m.tiles
		for (a in t) for (b in t) {
			val (ar, ac) = a.slot / 9 to a.slot % 9
			val (br, bc) = b.slot / 9 to b.slot % 9
			assertEquals(ar.compareTo(br), a.row.compareTo(b.row), "${a.name} vs ${b.name} rows")
			assertEquals(ac.compareTo(bc), a.col.compareTo(b.col), "${a.name} vs ${b.name} cols")
		}

		assertEquals(t.sortedBy { it.slot }, t.sortedWith(compareBy({ it.row }, { it.col })))

		assertEquals(t.size, t.map { it.row to it.col }.toSet().size)
	}

	@Test fun liveLoreAndStatus() {
		val sp = m.tiles.first { it.name == "Superpairs" }
		assertEquals("Charges: 3/3", ExpTableParser.status(sp.lore))
		assertEquals("On cooldown!", ExpTableParser.status(m.tiles.first { it.name == "Chronomatron" }.lore))
		assertEquals("Bonus: +1 click", ExpTableParser.status(m.tiles.first { it.slot == 20 }.lore))
		assertEquals("Bonus: +2 clicks", ExpTableParser.status(m.tiles.first { it.slot == 24 }.lore))
		assertEquals("Stored Experimental XP: 3,463", ExpTableParser.status(m.tiles.first { it.slot == 48 }.lore))

		assertNull(ExpTableParser.status(m.tiles.first { it.name == "Experience Bottles" }.lore))
		assertNull(ExpTableParser.status(m.tiles.first { it.name == "Close" }.lore))
		assertTrue(sp.rawLore.isNotEmpty())
	}

	@Test fun genericFillerDetection() {
		assertTrue(ExpTableParser.isFiller(SlotText(0, "§7 ", listOf("", " "))))
		assertFalse(ExpTableParser.isFiller(SlotText(0, "", listOf("Something"))))
		assertFalse(ExpTableParser.isFiller(SlotText(0, "Close")))
	}

	@Test fun nothingHardCodedNewItemsFollowLayout() {

		val extra = d.slots.map { if (it.slot == 26) SlotText(26, "New Thing", listOf("x")) else it }
		val m2 = ExpTableParser.parse(d.title, extra)!!
		assertEquals(6, m2.cols)
		assertEquals(0 to 5, m2.tiles.first { it.name == "New Thing" }.let { it.row to it.col })
		assertEquals(0 to 2, m2.tiles.first { it.name == "Superpairs" }.let { it.row to it.col })
	}

	@Test fun onlyTheMainTable() {
		assertNull(ExpTableParser.parse("Superpairs (Metaphysical)", d.slots))
		assertNull(ExpTableParser.parse("Chronomatron ➜ Stakes", d.slots))
		assertNull(ExpTableParser.parse("Experimentation Table RNG", d.slots))
		assertNull(ExpTableParser.parse("Experimentation Table", d.slots.map { SlotText(it.slot, "") }))
	}

	@Test fun ignoresPlayerInventory() {
		val withInv = d.slots + SlotText(60, "Diamond Sword", listOf("x"))
		assertEquals(10, ExpTableParser.parse(d.title, withInv, chestSize = 54)!!.tiles.size)
	}

	@Test fun clicksGoToTheRealSlot() {

		for (t in m.tiles) assertEquals(ExpClick(t.slot, 0), ExpTableClicks.route(m, t.slot, 0))
		val chrono = m.tiles.first { it.name == "Chronomatron" }
		assertEquals(29, chrono.slot)

		assertEquals(ExpClick(29, 1), ExpTableClicks.route(m, 29, 1))
		assertNull(ExpTableClicks.route(m, 29, 2))
		assertNull(ExpTableClicks.route(m, 0, 0))
		assertNull(ExpTableClicks.route(m, 60, 0))
	}

	@Test fun masterSwitchAndOwnMode() {
		assertTrue(ExpTableMenuGate.resolve { null })
		assertEquals(listOf("apex-menus"), ExpTableMenuGate.SWITCH_KEYS)
		assertTrue("apex-menus" in dev.ashtonite365.apex.features.experiments.ExperimentationTable.allOptions)
		assertFalse(ExpTableMenuGate.resolve { if (it == "apex-menus") false else null })
		assertTrue(ExpTableMenuGate.resolve { if (it == "apex-menus") "not a toggle" else null })
		assertTrue(ExpTableMenuGate.resolve { if (it == "experimentation-table-apex-menus" || it == "table-apex-menus") false else null })
		assertTrue(ExpTableMenuGate.apex(master = true, ownApex = true))
		assertFalse(ExpTableMenuGate.apex(master = false, ownApex = true))
		assertFalse(ExpTableMenuGate.apex(master = true, ownApex = false))
	}

	@Test fun registryReachesTheMainTableOnly() {
		assertFalse(MenuRegistry.denied("Experimentation Table"))
		for (t in listOf("Superpairs (Metaphysical)", "Chronomatron ➜ Stakes", "Ultrasequencer (Supreme)", "Experiment Over", "Experimentation Table RNG"))
			assertTrue(MenuRegistry.denied(t), t)
		assertTrue(ExpTableMenu.title.containsMatchIn("Experimentation Table"))
	}
}
