package dev.ashtonite365.apex.test.features.enchanting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.enchanting.ExperimentClickGuard
import dev.ashtonite365.apex.features.enchanting.ExperimentClickGuard.sequencerAllowed
import dev.ashtonite365.apex.features.enchanting.ExperimentClickGuard.shouldBlock
import dev.ashtonite365.apex.features.enchanting.ExperimentClickGuard.superpairsAllowed

class ExperimentClickGuardTest {
	private val order = mapOf(0 to 13, 1 to 5, 2 to 22)

	@Test
	fun sequencerCorrectAllowedWrongBlocked() {
		assertFalse(shouldBlock(true, sequencerAllowed(order, 1, true), 5))
		assertTrue(shouldBlock(true, sequencerAllowed(order, 1, true), 13))
		assertTrue(shouldBlock(true, sequencerAllowed(order, 1, true), 22))
	}

	@Test
	fun sequencerToggleOffAllowsEverything() {
		for (i in -1..30) assertFalse(shouldBlock(false, sequencerAllowed(order, 1, true), i))
	}

	@Test
	fun sequencerUnknownAllowsEverything() {
		assertNull(sequencerAllowed(order, 0, false))
		assertNull(sequencerAllowed(order, 3, true))
		assertNull(sequencerAllowed(emptyMap(), 0, true))
		for (i in -1..30) assertFalse(shouldBlock(true, sequencerAllowed(emptyMap(), 0, true), i))
	}

	private val remembered = mapOf(3 to "diamond", 7 to "diamond", 9 to "gold")

	@Test
	fun superpairsCorrectAllowedWrongBlocked() {

		val allowed = superpairsAllowed(setOf(3, 7, 9, 11), remembered + (15 to "iron"))
		assertEquals(setOf(3, 7, 11), allowed)
		assertFalse(shouldBlock(true, allowed, 7))
		assertFalse(shouldBlock(true, allowed, 11))
		assertTrue(shouldBlock(true, allowed, 9))
		assertTrue(shouldBlock(true, allowed, 15))
		assertFalse(shouldBlock(true, allowed, -1))
	}

	@Test
	fun superpairsToggleOffAllowsEverything() {
		val allowed = superpairsAllowed(setOf(3, 7, 9, 11), remembered)
		for (i in -1..20) assertFalse(shouldBlock(false, allowed, i))
	}

	@Test
	fun superpairsUnknownAllowsEverything() {

		val allowed = superpairsAllowed(setOf(9), mapOf(9 to "gold", 4 to "iron"))
		assertNull(allowed)
		for (i in -1..20) assertFalse(shouldBlock(true, allowed, i))
	}

	@Test
	fun guardOffByDefault() {

		val allowed = superpairsAllowed(setOf(3, 7), remembered)
		assertFalse(shouldBlock(ExperimentClickGuard.DEFAULT_ENABLED, allowed, 99))
	}

}
