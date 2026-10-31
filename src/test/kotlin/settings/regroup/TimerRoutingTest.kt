// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings.regroup

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.timers.BoardSort
import dev.ashtonite365.apex.features.timers.ShowOn
import dev.ashtonite365.apex.features.timers.TimerRouting
import dev.ashtonite365.apex.features.timers.TimerRouting.Item
import dev.ashtonite365.apex.util.render.SidePanel

class TimerRoutingTest {
	private val a = Item("a", "§eAlpha§r: 3s", 3.seconds)
	private val b = Item("b", "§ebeta§r: 1s", 1.seconds)
	private val c = Item("c", "§eCharlie§r: ready", null)
	private val d = Item("d", "§eDelta§r: 9s", 9.seconds)
	private val e = Item("e", "§eEcho§r: 2s", 2.seconds)
	private val choice = mapOf("a" to ShowOn.BOARD_1, "b" to ShowOn.BOARD_2, "c" to ShowOn.INVENTORY_BOARD, "d" to ShowOn.OWN_HUD, "e" to ShowOn.OFF)

	@Test fun routeSplitsByShowOn() {
		val r = TimerRouting.route(listOf(a, b, c, d, e), { choice.getValue(it) }, inventoryIncludesBoard1 = true)
		assertEquals(listOf(a), r.board1)
		assertEquals(listOf(b), r.board2)
		assertEquals(listOf(a, c), r.inventory, "inventory board lists Board 1 too")
		assertEquals(mapOf("d" to listOf(d)), r.own)
		val noB1 = TimerRouting.route(listOf(a, c), { choice.getValue(it) }, inventoryIncludesBoard1 = false)
		assertEquals(listOf(c), noB1.inventory)
	}

	@Test fun sortOrders() {
		val items = listOf(a, b, c, d)
		assertEquals(items, TimerRouting.sort(items, BoardSort.MANUAL))
		assertEquals(listOf(a, b, c, d), TimerRouting.sort(items, BoardSort.NAME), "by plain text, case-insensitive")
		assertEquals(listOf(c, b, a, d), TimerRouting.sort(items, BoardSort.TIME_LEFT), "ready first, then soonest")
	}

	@Test fun captureAndFade() {
		assertEquals(TimerRouting.Capture.DRAW, TimerRouting.capture(ShowOn.OWN_HUD))
		assertEquals(TimerRouting.Capture.DROP, TimerRouting.capture(ShowOn.OFF))
		for (s in listOf(ShowOn.BOARD_1, ShowOn.BOARD_2, ShowOn.INVENTORY_BOARD)) assertEquals(TimerRouting.Capture.TO_BOARD, TimerRouting.capture(s))
		assertEquals(0x00123456, TimerRouting.fade(0xFF123456.toInt(), 0))
		assertEquals(0xFF123456.toInt(), TimerRouting.fade(0xFF123456.toInt(), 100))
		assertEquals(0x7F123456, TimerRouting.fade(0xFF123456.toInt(), 50))
	}

	@Test fun showOnTable() {
		assertEquals(50, TimerRouting.SHOW_ON.size)
		assertEquals(TimerRouting.SHOW_ON.size, TimerRouting.SHOW_ON.map { it.first }.toSet().size, "unique ids")
		assertEquals("ability-cooldowns", TimerRouting.showOnId("abilities"))
		for (id in TimerRouting.OWN_HUD_ELEMENTS.values) assertFalse(TimerRouting.needsNewOwnHud(id))
		for (id in TimerRouting.LINE_TIMERS) assertFalse(TimerRouting.needsNewOwnHud(id))
		assertTrue(TimerRouting.SHOW_ON.any { TimerRouting.needsNewOwnHud(it.first) })
	}

	@Test fun panelOpacityKeepsText() {
		val p = SidePanel.withOpacity(SidePanel.DARK, 25)
		assertEquals(SidePanel.DARK.text, p.text)
		assertEquals(0x3F, p.fill ushr 24)
		assertEquals(SidePanel.DARK.fill and 0xFFFFFF, p.fill and 0xFFFFFF)
		assertEquals(SidePanel.DARK, SidePanel.withOpacity(SidePanel.DARK, 100))
	}
}
