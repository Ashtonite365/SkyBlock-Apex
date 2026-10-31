// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.MenuText

class MenuTextTest {
	@Test
	fun clean() {
		assertEquals("a b", MenuText.clean("§a a \u00a0 §lb§"))
	}

	@Test
	fun numbers() {
		assertEquals(1234.5, MenuText.number("§61,234.5 coins")!!, 1e-9)
		assertEquals(1.2e6, MenuText.number("1.2M")!!, 1e-6)
		assertEquals(3e3, MenuText.number("3 k")!!, 1e-9)
		assertEquals(5.0, MenuText.number("5 kittens")!!, 1e-9)
		assertNull(MenuText.number("lots"))
		assertNull(MenuText.number(""))
	}

	@Test
	fun durations() {
		assertEquals(93784L, MenuText.durationSeconds("1d 2h 3m 4s"))
		assertEquals(23 * 3600L, MenuText.durationSeconds("23 hours"))
		assertEquals(60L, MenuText.durationSeconds("a minute"))
		assertEquals(330L, MenuText.durationSeconds("5m30s"))
		assertEquals(3723L, MenuText.durationSeconds("01:02:03"))
		assertEquals(30 * 86400L * 2, MenuText.durationSeconds("2 months"))
		assertNull(MenuText.durationSeconds("soon"))
		assertNull(MenuText.durationSeconds("50,000 coins"))
		assertEquals("1d 2h", MenuText.formatDuration(93784))
		assertEquals("4.56M", MenuText.coins(4_560_000.0))
		assertEquals("950", MenuText.coins(950.0))
	}
}
