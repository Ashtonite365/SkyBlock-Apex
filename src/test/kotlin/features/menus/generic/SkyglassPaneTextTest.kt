// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.generic

import dev.ashtonite365.apex.features.menus.generic.GenericWrapper
import dev.ashtonite365.apex.features.menus.generic.GenericWrapper.PaneIn
import dev.ashtonite365.apex.features.menus.generic.GenericWrapper.Plan
import dev.ashtonite365.apex.features.menus.style.StyledChestLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkyglassPaneTextTest {
	@Test fun emptyCellsHiddenInNavMenusShownInInventories() {
		for (t in listOf("SkyBlock Menu", "Essence Guide", "Collections", "Bestiary")) assertTrue(GenericWrapper.hideEmptyCells(true, t), t)
		for (t in listOf("Chest", "Large Chest", "Barrel", "Ender Chest (2/9)", "Large Backpack (Slot #4)", "Storage", "Wardrobe (1/2)", "Personal Vault"))
			assertFalse(GenericWrapper.hideEmptyCells(true, t), t)
	}

	@Test fun forgeIsUnchanged() {
		assertFalse(GenericWrapper.hideEmptyCells(false, "SkyBlock Menu"))
	}

	@Test fun lorePaneRendersAsTextAndDuplicatesShowOnce() {
		val lore = listOf("§7Line one", "§7Line two")
		val plan = GenericWrapper.planPanes(listOf(
			PaneIn(3, true, "§aInfo", lore), PaneIn(5, true, "§aInfo", lore), PaneIn(7, true, " ", emptyList()),
			PaneIn(8, true, "§eJust a name", emptyList()), PaneIn(9, false, "Diamond", lore)))
		assertEquals(Plan.Text(listOf("§aInfo", "§7Line one", "§7Line two")), plan[3])
		assertEquals(Plan.Hidden, plan[5])
		assertEquals(Plan.Empty, plan[7])
		assertEquals(Plan.Text(listOf("§eJust a name")), plan[8])
		assertFalse(9 in plan)
	}

	@Test fun repeatedLinesInOnePaneAreDeduped() {
		val plan = GenericWrapper.planPanes(listOf(PaneIn(0, true, "§aA", listOf("§7x", "§7x", "", "§8x", "§7y"))))
		assertEquals(Plan.Text(listOf("§aA", "§7x", "§7y")), plan[0])
	}

	@Test fun textSpansFreeCellsInTheSameRowOnly() {
		assertEquals(18 + 2 * 20, GenericWrapper.span(3, setOf(4, 5, 7), 20, 18))
		assertEquals(18, GenericWrapper.span(8, setOf(9, 10), 20, 18))
	}

	@Test fun clickOnATextCellHitsItsRealSlot() {

		val l = StyledChestLayout.build(640, 360, 6, false)
		for (i in 0 until 54) {
			val c = l.cellScreen(i)
			val hits = (0 until 54).filter { j -> val d = l.cellScreen(j); c.x + c.w / 2 in d.x until d.x + d.w && c.y + c.h / 2 in d.y until d.y + d.h }
			assertEquals(listOf(i), hits)
		}
	}
}
