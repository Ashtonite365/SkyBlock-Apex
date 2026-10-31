package dev.ashtonite365.apex.test.features.kit.kuudra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.kuudra.PearlLineups

class PearlLineupsTest {
	private val t = PearlLineups.table

	@Test
	fun bundledTableLoads() {
		assertEquals(listOf("Shop", "Triangle", "X", "xCannon", "Square", "Equals", "Slash"), t.map { it.supply })
		assertEquals(6, t.first { it.supply == "Square" }.targets.size)
		assertTrue(t.all { it.start.isNotEmpty() && it.targets.isNotEmpty() })
	}

	@Test
	fun closestLineupFirst() {
		assertEquals("Slash", PearlLineups.visible(t, -115.0, 77.0, -69.0, true).single().supply)
		assertEquals("Shop", PearlLineups.ordered(t, -86.0, 78.0, -129.0).first().supply)
		assertEquals(t.size, PearlLineups.visible(t, 0.0, 0.0, 0.0, false).size)
	}
}
