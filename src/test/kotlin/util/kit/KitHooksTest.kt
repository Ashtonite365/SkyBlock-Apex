// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.kit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.kit.BossPhase
import dev.ashtonite365.apex.util.kit.BossPhaseTracker
import dev.ashtonite365.apex.util.kit.ServerTicks

class KitHooksTest {
	@BeforeEach
	fun reset() {
		ServerTicks.reset()
		BossPhaseTracker.reset()
	}

	@Test
	fun tickCounterCountsDistinctPings() {
		assertTrue(ServerTicks.accept(1))
		assertTrue(ServerTicks.accept(2))
		assertFalse(ServerTicks.accept(2))
		assertTrue(ServerTicks.accept(-5))
		assertEquals(3L, ServerTicks.totalTicks)
		assertEquals(2L, ServerTicks.since(1L))
		assertEquals(0L, ServerTicks.since(99L))
	}

	@Test
	fun tickCounterReset() {
		ServerTicks.accept(1)
		ServerTicks.reset()
		assertEquals(0L, ServerTicks.totalTicks)
		assertTrue(ServerTicks.accept(1))
	}

	@Test
	fun matchesF7Phases() {
		assertEquals(BossPhase.MAXOR, BossPhase.match("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!"))
		assertEquals(BossPhase.STORM, BossPhase.match("[BOSS] Storm: Pathetic Maxor, just like expected."))
		assertEquals(BossPhase.GOLDOR, BossPhase.match("[BOSS] Goldor: Who dares trespass into my domain?"))
		assertEquals(BossPhase.GOLDOR_CORE, BossPhase.match("The Core entrance is opening!"))
		assertEquals(BossPhase.NECRON, BossPhase.match("[BOSS] Necron: You went further than any human before, congratulations."))
		assertEquals(BossPhase.WITHER_KING, BossPhase.match("[BOSS] Wither King: You... again?"))
		assertNull(BossPhase.match("Party > [MVP+] x: [BOSS] Storm: Pathetic Maxor, just like expected."))
	}

	@Test
	fun matchesKuudraPhases() {
		assertEquals(BossPhase.KUUDRA_SUPPLIES, BossPhase.match("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!"))
		assertEquals(BossPhase.KUUDRA_DPS, BossPhase.match("Steve destroyed one of Kuudra's pods!"))
		assertEquals(BossPhase.Boss.KUUDRA, BossPhase.KUUDRA_END.boss)
		assertNull(BossPhase.match("hello"))
	}

	@Test
	fun trackerDedupesAndChains() {
		assertNull(BossPhaseTracker.handle("random chat", 1))
		val a = BossPhaseTracker.handle("[BOSS] Storm: Pathetic Maxor, just like expected.", 10)!!
		assertEquals(BossPhase.STORM, a.phase)
		assertNull(a.previous)
		assertNull(BossPhaseTracker.handle("[BOSS] Storm: Pathetic Maxor, just like expected.", 11))
		val b = BossPhaseTracker.handle("[BOSS] Goldor: Who dares trespass into my domain?", 400)!!
		assertEquals(BossPhase.STORM, b.previous)
		assertEquals(400L, BossPhaseTracker.phaseStartTick)
		assertEquals(BossPhase.GOLDOR, BossPhaseTracker.current)
	}
}
