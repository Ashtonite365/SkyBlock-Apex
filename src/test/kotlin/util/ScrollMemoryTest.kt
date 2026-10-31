// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.util.ScrollMemory

class ScrollMemoryTest {
	private var now = 0L
	private val realClock = ScrollMemory.clock

	@BeforeEach fun setUp() { ScrollMemory.clear(); ScrollMemory.clock = { now } }
	@AfterEach fun tearDown() { ScrollMemory.clear(); ScrollMemory.clock = realClock }

	private class Menu(settle: Boolean = false, key: () -> String) { var scroll by ScrollMemory.int(settle, key) }

	@Test fun reopenedMenuRestoresItsOwnPosition() {
		Menu { "a" }.scroll = 120
		Menu { "b" }.scroll = 30
		assertEquals(120, Menu { "a" }.scroll)
		assertEquals(30, Menu { "b" }.scroll)
		assertEquals(0, Menu { "c" }.scroll, "never scrolled: top")
	}

	@Test fun subPagesHaveTheirOwnPosition() {
		var tab = "one"
		val m = Menu { "menu|$tab" }
		m.scroll = 50
		now += ScrollMemory.SETTLE_MS
		tab = "two"; assertEquals(0, m.scroll)
		m.scroll = 7
		tab = "one"; assertEquals(50, m.scroll)
	}

	@Test fun earlyClampWhileFillingKeepsMemoryLaterClampSaves() {
		Menu { "k" }.scroll = 200
		val m = Menu(settle = true) { "k" }
		m.scroll = 0
		assertEquals(200, m.scroll, "ignored while settling")
		assertEquals(200, ScrollMemory.get("k"))
		now += ScrollMemory.SETTLE_MS
		m.scroll = 80
		assertEquals(80, ScrollMemory.get("k"))
	}

	@Test fun withoutSettleAResetIsImmediate() {
		val m = Menu { "r" }
		m.scroll = 90; m.scroll = 0
		assertEquals(0, m.scroll); assertEquals(0, ScrollMemory.get("r"))
	}

	@Test fun clamp() {
		assertEquals(40, ScrollMemory.clamp(100, 40)); assertEquals(0, ScrollMemory.clamp(-5, 40)); assertEquals(0, ScrollMemory.clamp(10, -3))
	}

	@Test fun scrollViewRestoresAndClampsShorterContent() {
		val v1 = ScrollView { "view" }
		v1.layout(MenuBounds(0, 0, 100, 200), 1000)
		repeat(5) { v1.scroll(-3.0) }
		val off = v1.offset
		assertEquals(true, off > 0)
		val v2 = ScrollView { "view" }
		v2.layout(MenuBounds(0, 0, 100, 200), 1000)
		assertEquals(off, v2.offset, "new instance, same key: restored")
		val v3 = ScrollView { "view" }
		v3.layout(MenuBounds(0, 0, 100, 200), 250)
		assertEquals(50, v3.offset, "content shorter: clamped")
		val v4 = ScrollView { "view" }
		v4.layout(MenuBounds(0, 0, 100, 200), 1000)
		assertEquals(off, v4.offset, "clamp did not lose the remembered position")
		assertEquals(0, ScrollView { "other" }.also { it.layout(MenuBounds(0, 0, 100, 200), 1000) }.offset)
	}
}
