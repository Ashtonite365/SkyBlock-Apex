// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.CommunityParser
import dev.ashtonite365.apex.features.menus.skyblock.UpgradeState
import dev.ashtonite365.apex.features.menus.skyblock.UpgradesLayout
import dev.ashtonite365.apex.features.menus.skyblock.UpgradesModel
import dev.ashtonite365.apex.features.menus.skyblock.UpgradesParser
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CommunityUpgradesTest {
	private val d = RealDumpFixtures.load("community-upgrades-2042")
	private val m = CommunityParser.parse(d.title, d.slots)!!
	private val u: UpgradesModel = m.upgrades!!

	private val measure: (String) -> Int = { MenuText.clean(it).length * 6 }

	@Test
	fun vanillaRowsAndColumns() {
		assertEquals("Account & Profile Upgrades", m.selectedTab)

		assertEquals(listOf(2, 3, 4), u.rows)

		assertEquals(listOf(0, 2, 3, 4, 5, 6, 7), u.columns)
		fun row(r: Int) = u.cells.filter { it.row == r }.map { it.slot }
		assertEquals(listOf(18, 20, 21, 22, 23, 24), row(2))
		assertEquals(listOf(27, 29, 30, 31, 32, 33, 34), row(3))
		assertEquals(listOf(36, 38, 39, 40, 41, 42), row(4))
		assertEquals(listOf("Profile Upgrades", "Account Upgrades", "Account Upgrades"), u.cells.filter { it.header }.map { it.name })
		assertTrue(u.cells.none { it.slot == 49 || it.name.isBlank() }, "no account item, no filler pane in the grid")
		assertEquals(listOf("Profile: Nothing going on!", "Account: Armor Wardrobe Slots I (DONE!)"), u.status)
	}

	@Test
	fun layoutMatchesVanillaPositions() {
		val lay = UpgradesLayout.place(u, 600, measure)
		val by = lay.placed.associateBy { it.cell.slot }

		for (col in u.columns) assertEquals(1, u.cells.filter { it.col == col }.map { by.getValue(it.slot).x }.distinct().size, "column $col")

		assertEquals(u.columns, u.columns.sortedBy { lay.columnX.getValue(it) })
		for (r in u.rows) assertEquals(1, u.cells.filter { it.row == r }.map { by.getValue(it.slot).y }.distinct().size, "row $r")
		val rowY = u.rows.map { r -> by.getValue(u.cells.first { it.row == r }.slot).y }
		assertEquals(rowY.sorted(), rowY); assertEquals(rowY.size, rowY.distinct().size)
		for (a in lay.placed) for (b in lay.placed) if (a !== b)
			assertFalse(a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h, "${a.cell.slot} overlaps ${b.cell.slot}")
		assertTrue(lay.placed.all { it.x >= 0 && it.x + it.w <= 600 })

		assertTrue(lay.placed.filter { !it.cell.header }.all { it.x > by.getValue(18).x })
	}

	@Test
	fun noLoreLineIsLost() {
		for (width in listOf(300, 450, 600, 900)) {
			val lay = UpgradesLayout.place(u, width, measure)
			for (p in lay.placed) {
				val shown = (p.nameLines + p.body.filterNotNull()).joinToString(" ") { MenuText.clean(it) }
				val words = shown.split(' ').filter { it.isNotEmpty() }.joinToString("")
				val want = (listOf(p.cell.name) + p.cell.lore).joinToString("") { MenuText.clean(it).replace(" ", "") }
				assertEquals(want, words, "slot ${p.cell.slot} at width $width")
				assertFalse(shown.contains('…'), "never ellipsized")
				assertEquals(p.cell.lore.count { MenuText.clean(it).isEmpty() }, p.body.count { it == null }, "blank lines kept as spacers")
			}
		}

		val shownSlots = u.cells.map { it.slot } + m.tabs.map { it.slot } + listOfNotNull(m.accountSlot)
		val real = d.slots.filter { it.cleanName.isNotBlank() && !it.cleanName.contains("Categories") }.map { it.slot }
		assertEquals(real.sorted(), shownSlots.sorted())
		assertNotNull(u.divider)
		assertEquals(d.slots.first { it.slot == 49 }.cleanLore.filter { it.isNotBlank() }, u.account, "whole account item in the footer")
	}

	@Test
	fun longLinesWrap() {
		val lay = UpgradesLayout.place(u, 450, measure)
		val island = lay.placed.first { it.cell.name == "Island Size II" }
		val inner = island.w - UpgradesLayout.PAD * 2
		assertTrue(island.body.filterNotNull().all { measure(it) <= inner }, "every body line fits the cell")
		assertTrue(island.body.filterNotNull().size > island.cell.lore.count { it.isNotBlank() }, "long lines wrapped onto extra lines")
		assertTrue(lay.placed.all { p -> p.nameLines.all { measure(it) <= p.w - UpgradesLayout.PAD * 2 } }, "names wrap too")
		val header = lay.placed.first { it.cell.slot == 18 }
		assertTrue(header.body.filterNotNull().all { measure(it) <= header.w - UpgradesLayout.PAD * 2 })

		val long = "Cost: " + "A".repeat(80)
		val wrapped = UpgradesLayout.wrapLore(listOf(long), 60, measure).filterNotNull()
		assertTrue(wrapped.size > 1)
		assertEquals(long.replace(" ", ""), wrapped.joinToString("") { MenuText.clean(it).replace(" ", "") })

		assertTrue(lay.placed.all { it.h >= UpgradesLayout.cellHeight(it) })
	}

	@Test
	fun statesFromLore() {
		fun st(slot: Int) = u.cells.first { it.slot == slot }.state
		assertEquals(UpgradeState.CANNOT_AFFORD, st(20))
		assertEquals(UpgradeState.MAXED, st(21))
		assertEquals(UpgradeState.AVAILABLE, st(22))
		assertEquals(UpgradeState.READY_TO_CLAIM, st(29))
		assertEquals(UpgradeState.BLOCKED, st(30))
		assertEquals(UpgradeState.IN_PROGRESS, UpgradesParser.state(listOf("Account Upgrade", "", "Time left: 2d 3h")))
		assertNull(st(18))
	}

	@Test
	fun otherTabsAreNotUpgrades() {
		val shop = RealDumpFixtures.load("community-shop")
		assertNull(CommunityParser.parse(shop.title, shop.slots)!!.upgrades, "City Projects tab keeps its cards")
		assertNull(UpgradesParser.parse("Bank Upgrades", d.slots), "title must be the Community Shop")
	}
}
