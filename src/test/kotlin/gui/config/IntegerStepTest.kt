// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.config

import dev.ashtonite365.apex.gui.config.IntegerStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntegerStepTest {
	@Test fun zoomFactorStepsByOneUp() {
		var v = 2; val seen = mutableListOf(v)
		repeat(10) { v = IntegerStep.next(v, 1, 2, 10); seen += v }
		assertEquals(listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 10, 10), seen)
	}
	@Test fun zoomFactorStepsByOneDown() {
		var v = 10; val seen = mutableListOf(v)
		repeat(10) { v = IntegerStep.next(v, -1, 2, 10); seen += v }
		assertEquals(listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 2, 2), seen)
	}
	@Test fun shiftOnSmallRangeIsStillOne() {
		assertEquals(3, IntegerStep.next(2, 1, 2, 10, shift = true))
		assertEquals(9, IntegerStep.next(10, -1, 2, 10, shift = true))
	}
	@Test fun shiftOnBigRangeIsTenthAndNeverJumpsEnd() {
		assertEquals(12, IntegerStep.bigStep(8, 128))
		assertEquals(52, IntegerStep.next(40, 1, 8, 128, shift = true))
		for (min in 0..5) for (max in min..min + 30) assertTrue(IntegerStep.bigStep(min, max) <= maxOf(1, (max - min) / 2))
	}
	@Test fun neverWraps() {
		assertEquals(1, IntegerStep.next(1, -1, 1, 10))
		assertEquals(10, IntegerStep.next(10, 1, 1, 10))
	}
}
