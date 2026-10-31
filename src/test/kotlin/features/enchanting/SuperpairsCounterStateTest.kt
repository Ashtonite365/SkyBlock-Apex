package dev.ashtonite365.apex.test.features.enchanting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.enchanting.SuperpairsCard
import dev.ashtonite365.apex.features.enchanting.SuperpairsCounterState

class SuperpairsCounterStateTest {
	private val hidden = SuperpairsCard(null)
	private fun c(k: String) = SuperpairsCard(k)
	private fun board(vararg revealed: Pair<Int, SuperpairsCard>) =
		MutableList(10) { hidden }.also { b -> revealed.forEach { b[it.first] = it.second } }

	@Test fun rememberedAcrossFlips() {
		val s = SuperpairsCounterState()
		s.update(board(0 to c("a"), 1 to c("b")), null)
		assertEquals(0, s.rememberedPairs); assertEquals(0, s.matchedPairs)
		s.update(board(), null)
		s.update(board(5 to c("a"), 6 to c("c")), null)
		assertEquals(1, s.rememberedPairs); assertEquals(0, s.matchedPairs)
	}

	@Test fun matchedPairsNotCountedAsRemembered() {
		val s = SuperpairsCounterState()
		s.update(board(0 to c("a"), 3 to c("b")), null)
		s.update(board(), null)
		s.update(board(0 to c("a"), 5 to c("a"), 7 to c("b")), null)
		assertEquals(1, s.matchedPairs); assertEquals(1, s.rememberedPairs)
	}

	@Test fun powerupsIgnored() {
		val s = SuperpairsCounterState()
		s.update(board(0 to SuperpairsCard("x", true), 1 to SuperpairsCard("x", true)), null)
		assertEquals(0, s.rememberedPairs); assertEquals(0, s.matchedPairs)
	}

	@Test fun clicksLeftPrefersGuiThenDerives() {
		val s = SuperpairsCounterState()
		repeat(3) { s.onClick() }
		assertNull(s.clicksLeft(null))
		assertEquals(9, s.clicksLeft(12))
		s.update(board(), 7)
		assertEquals(7, s.clicksLeft(12))
		s.reset(); assertEquals(0, s.clicks)
	}

	@Test fun parsesClicksLine() {
		assertEquals(14, SuperpairsCounterState.parseClicksLeft(listOf("Superpairs", "Clicks Remaining: 14")))
		assertEquals(3, SuperpairsCounterState.parseClicksLeft(listOf("Clicks left 3")))
		assertNull(SuperpairsCounterState.parseClicksLeft(listOf("Timer: 10s")))
	}

	@Test fun rememberedCountsBothCardsSeparately() {
		val s = SuperpairsCounterState()
		s.update(board(2 to c("a")), null); s.update(board(), null)
		s.update(board(8 to c("a")), null); s.update(board(), null)
		assertEquals(1, s.rememberedPairs); assertEquals(0, s.matchedPairs)
	}

	@Test fun powerupsMixedWithCards() {
		val s = SuperpairsCounterState()
		s.update(board(0 to c("a"), 1 to SuperpairsCard("a", true)), null)
		assertEquals(0, s.rememberedPairs); assertEquals(0, s.matchedPairs)
	}

	@Test fun clicksLeftNeverNegative() {
		val s = SuperpairsCounterState()
		repeat(5) { s.onClick() }
		assertEquals(0, s.clicksLeft(3))
	}

	@Test fun resetClearsEverything() {
		val s = SuperpairsCounterState()
		s.update(board(0 to c("a"), 1 to c("a"), 2 to c("b")), 4)
		s.update(board(0 to c("a"), 1 to c("a"), 5 to c("b")), 4)
		s.onClick()
		s.reset()
		assertEquals(0, s.clicks); assertNull(s.hypixelClicksLeft)
		assertEquals(0, s.rememberedPairs); assertEquals(0, s.matchedPairs)
		assertEquals(10, s.clicksLeft(10))
	}

	@Test fun parsesSpecLines() {
		assertEquals(5, SuperpairsCounterState.parseClicksLeft(listOf("Clicks Remaining: 5")))
		assertEquals(3, SuperpairsCounterState.parseClicksLeft(listOf("Clicks Left 3")))
	}
}
