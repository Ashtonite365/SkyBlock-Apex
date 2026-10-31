// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.splits

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.splits.KitSplitDefs
import dev.ashtonite365.apex.features.kit.splits.RunSummary
import dev.ashtonite365.apex.features.kit.splits.SplitRun

class KitSplitsLogicTest {
	@Test
	fun kuudraRun() {
		val r = SplitRun(KitSplitDefs.kuudra)
		assertEquals(-1, r.onMessage("[NPC] Elle: OMG! Great work collecting my supplies!", 0, 0))
		assertEquals(0, r.onMessage("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!", 1000, 0))
		assertEquals(1, r.onMessage("[NPC] Elle: OMG! Great work collecting my supplies!", 31000, 600))
		val rows = r.rows(41000, 800)
		assertEquals(30000, rows[0].time); assertEquals(600, rows[0].ticks)
		assertTrue(rows[1].isCurrent); assertEquals(10000, rows[1].time)
		assertEquals("Total", rows.last().name); assertEquals(40000, rows.last().time)
		assertEquals("30.00", SplitRun.formatTicks(600))
	}

	@Test
	fun bossPhases() {
		assertNull(KitSplitDefs.bossRun("F4"))
		val r = KitSplitDefs.bossRun("M7")!!
		r.onMessage("[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", 0, 0)
		r.onMessage("[BOSS] Storm: Pathetic Maxor, just like expected.", 5000, 100)
		assertEquals("§5Maxor", r.rows(6000, 120)[0].name)
		assertEquals(0, KitSplitDefs.floorNumber("E")); assertEquals(7, KitSplitDefs.floorNumber("F7"))
	}

	@Test
	fun runSummary() {
		val s = RunSummary()
		assertEquals(false, s.feed("hello"))
		assertTrue(s.feed("        ☠ Defeated Maxor, Storm, Goldor, and Necron in 05m 12s"))
		assertTrue(s.feed("   Team Score: 317 (S+)"))
		assertTrue(s.feed("   +312 Bits"))
		assertNull(s.take())
		assertTrue(s.feed("   Secrets Found: 12"))
		val out = s.take()
		assertNotNull(out); assertEquals(4, out!!.size)
	}
}
