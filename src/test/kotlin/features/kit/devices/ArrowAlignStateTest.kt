package dev.ashtonite365.apex.test.features.kit.devices

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.devices.ArrowAlignLogic
import dev.ashtonite365.apex.features.kit.devices.ArrowAlignState

class ArrowAlignStateTest {
	private val sol = ArrowAlignLogic.SOLUTIONS[6]

	private fun board(i: Int, off: Int) = sol.mapIndexed { k, r -> if (r == -1) -1 else if (k == i) (r - off + 8) % 8 else r }

	@Test
	fun predictionBlocksOverRotationBeforeSync() {
		val i = sol.indexOfFirst { it != -1 }
		val s = ArrowAlignState()
		s.update(board(i, 1), 0)
		assertEquals(mapOf(i to 1), s.clicks())
		assertFalse(s.shouldBlock(i, false))
		s.onClick(i, 10)
		s.update(board(i, 1), 200)
		assertTrue(s.clicks().isEmpty())
		assertTrue(s.shouldBlock(i, false))
		assertFalse(s.shouldBlock(i, true))
		s.update(board(i, 1), 10 + ArrowAlignState.HOLD_MS)
		assertEquals(mapOf(i to 1), s.clicks())
	}

	@Test
	fun noBlockWithoutKnownSolutionOrFrame() {
		val s = ArrowAlignState()
		s.update(List(25) { -1 }, 0)
		assertFalse(s.shouldBlock(0, false))
		assertFalse(s.shouldBlock(null, false))
	}
}
