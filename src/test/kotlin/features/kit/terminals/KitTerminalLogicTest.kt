package dev.ashtonite365.apex.test.features.kit.terminals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.terminals.KitTerminalLogic
import dev.ashtonite365.apex.features.kit.terminals.KitTerminalLogic.RubixMode

class KitTerminalLogicTest {
	@Test
	fun rubixFewest() {

		val m = KitTerminalLogic.rubixSolve(mapOf(12 to 0, 13 to 1, 14 to 4), RubixMode.FEWEST_CLICKS)
		assertEquals(mapOf(13 to -1, 14 to 1), m)
	}

	@Test
	fun rubixLeftOnly() {
		val m = KitTerminalLogic.rubixClicksFor(0, mapOf(13 to 1), RubixMode.LEFT_CLICKS_ONLY)
		assertEquals(mapOf(13 to 4), m)
		assertTrue(KitTerminalLogic.rubixSolve(mapOf(1 to 2, 2 to 2), RubixMode.LEFT_CLICKS_ONLY).isEmpty())
	}

	@Test
	fun melodyAndProt() {
		assertTrue(KitTerminalLogic.melodyReady(3, 21))
		assertFalse(KitTerminalLogic.melodyReady(3, 22))
		assertTrue(KitTerminalLogic.firstClickBlocked(1000, 1100, 200))
		assertFalse(KitTerminalLogic.firstClickBlocked(1000, 1300, 200))
		assertFalse(KitTerminalLogic.firstClickBlocked(1000, 1000, 0))
	}
}
