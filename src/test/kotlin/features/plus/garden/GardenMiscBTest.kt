// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers as T
import dev.ashtonite365.apex.features.garden.GardenMiscB as B

class GardenMiscBTest {
	@Test
	fun seasons() {
		assertEquals(0, B.seasonIndex(T.EPOCH))
		assertEquals(1, B.seasonIndex(T.EPOCH + 3 * T.MONTH))
		assertEquals(3, B.seasonIndex(T.EPOCH + T.YEAR - 1))
		assertEquals(0, B.seasonIndex(T.EPOCH + 5 * T.YEAR + T.DAY))
	}

	@Test
	fun stereoAndLogbook() {
		assertEquals("Pretty Fly", B.parsePlaying(listOf("Vinyl", " Now Playing: Pretty Fly ")))
		assertEquals("Fly", B.vinylPests["Pretty Fly"])
		assertEquals(50.0, B.acceptRate(listOf("Times Visited: 22", "Offers Accepted: 11")))
		assertEquals(null, B.acceptRate(listOf("Times Visited: 22")))
	}
}
