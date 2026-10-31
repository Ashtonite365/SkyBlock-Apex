// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenJacobExtras

class GardenJacobExtrasTest {
	@Test
	fun pbGain() {
		assertEquals("COCOA_BEANS", GardenJacobExtras.cropKey("Cocoa Beans"))

		val msg = GardenJacobExtras.gainMessage(1_400_694.0, 1_176_372.0, 46.69, "Potato", 300, false)!!
		assertTrue(msg.contains("7.48☘ Potato Fortune"), msg)
		assertNull(GardenJacobExtras.gainMessage(1_400_694.0, 1_176_372.0, 46.69, "Potato", 300, true))
	}

	@Test
	fun contests() {
		val start = GardenJacobExtras.contestStart("Early Spring 1st, Year 1")
		assertEquals(1560275700000L, start)
		assertEquals(1560275700000L + 446400000L / 12 + 1_200_000L * 30, GardenJacobExtras.contestStart("Spring 31st, Year 1"))
		assertNull(GardenJacobExtras.contestStart("Upcoming Contests"))
		assertEquals("GOLD", GardenJacobExtras.medal(listOf("Your score: 1", "You placed in the GOLD bracket!")))
	}
}
