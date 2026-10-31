// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenVisitors

class GardenVisitorsTest {
	@Test
	fun timer() {
		assertEquals("11m", GardenVisitors.parseNextVisitor(listOf("Garden", " Next Visitor: 11m")))
		assertEquals("Queue Full!", GardenVisitors.parseNextVisitor(listOf(" Next Visitor: Queue Full!")))
		assertEquals(null, GardenVisitors.parseNextVisitor(listOf("nothing")))
	}

	@Test
	fun rewards() {
		assertEquals(listOf("Overgrown Grass"), GardenVisitors.rareRewardsIn(listOf("Rewards:", " +1 Overgrown Grass", " +5 Copper")))
		assertEquals(listOf("Supreme Voter's Badge"), GardenVisitors.rareRewardsIn(listOf(" Supreme Voter's Badge")))
	}
}
