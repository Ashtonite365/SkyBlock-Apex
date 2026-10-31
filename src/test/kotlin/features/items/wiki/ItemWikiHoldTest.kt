// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.ItemWikiHint
import dev.ashtonite365.apex.features.items.wiki.WikiHold
import dev.ashtonite365.apex.features.items.wiki.WikiHold.State

class ItemWikiHoldTest {
	private fun filling(s: State) = s as State.Filling

	@Test fun nothingRendersWhenTheKeyIsNotHeld() {
		val h = WikiHold(1500, 150)
		for (t in 0L..5000L step 100) {
			val s = h.update(t, false, "HYPERION")
			assertEquals(State.Hidden, s)
			assertNull(ItemWikiHint.lines(s, "J"), "no hint and no bar at $t ms")
		}

		assertNull(ItemWikiHint.lines(h.update(6000, true, null), "J"))
		assertNull(ItemWikiHint.lines(State.Fire, "J"))
	}

	@Test fun fillIsLinearInRealTimeThenFiresOnce() {
		val h = WikiHold(1000)
		assertEquals(State.Filling(0f, 0f), h.update(0, true, "A"), "starts empty")
		for ((t, f) in listOf(250L to 0.25f, 500L to 0.5f, 750L to 0.75f, 990L to 0.99f)) {
			val s = filling(h.update(t, true, "A"))
			assertEquals(f, s.raw, 1e-4f, "raw at $t ms")
			assertEquals(f, s.shown, 1e-4f, "shown == elapsed / hold at $t ms (no easing)")
		}
		assertEquals(State.Fire, h.update(1000, true, "A"), "opens exactly when full")
		assertEquals(State.Hidden, h.update(1001, true, "A"), "no second fire while still held")
		assertEquals(State.Hidden, h.update(9000, true, "A"))
		assertEquals(State.Hidden, h.update(9100, false, "A"))
		assertEquals(State.Filling(0f, 0f), h.update(9200, true, "A"), "release re-arms")
	}

	@Test fun linearForTheConfiguredHoldDuration() {
		for (hold in listOf(300L, 1500L, 5000L)) {
			val h = WikiHold(hold)
			h.update(0, true, "A")
			for (q in 1..3) {
				val s = filling(h.update(hold * q / 4, true, "A"))
				assertEquals(q / 4f, s.shown, 1e-3f, "$q/4 of $hold ms")
			}
			assertTrue(h.update(hold - 1, true, "A") is State.Filling)
			assertEquals(State.Fire, h.update(hold, true, "A"))
		}
	}

	@Test fun progressIsIndependentOfFrameRate() {
		fun run(frameMs: Double): Pair<Map<Long, Float>, Long> {
			val h = WikiHold(1500)
			val samples = HashMap<Long, Float>()
			var fired = -1L
			var t = 0.0
			while (t <= 2000.0 && fired < 0) {
				val now = t.toLong()
				when (val s = h.update(now, true, "A")) {
					is State.Filling -> samples[now] = s.shown
					State.Fire -> fired = now
					else -> {}
				}
				t += frameMs
			}
			return samples to fired
		}
		val (fast, fastFire) = run(1000.0 / 300)
		val (slow, slowFire) = run(1000.0 / 30)
		for ((now, f) in fast + slow) assertEquals(now / 1500f, f, 1e-4f, "fill at $now ms")
		assertTrue(fastFire in 1500L..1504L, "fast fired at $fastFire")
		assertTrue(slowFire in 1500L..1534L, "slow fired at $slowFire")

		val h = WikiHold(1500); h.update(0, true, "A")
		val fills = (1..14).map { filling(h.update(it * 100L, true, "A")).shown }
		val steps = fills.zipWithNext { a, b -> b - a }
		for (d in steps) assertEquals(steps.first(), d, 1e-4f)
	}

	@Test fun optionalDelayStillFillsLinearly() {
		val h = WikiHold(1000, 150)
		assertEquals(State.Filling(0f, 0f), h.update(0, true, "A"))
		assertEquals(State.Filling(0f, 0f), h.update(150, true, "A"))
		assertEquals(0.5f, filling(h.update(650, true, "A")).shown, 1e-4f)
		assertEquals(State.Fire, h.update(1150, true, "A"))
	}

	@Test fun releasingEarlyCancelsAndResets() {
		val h = WikiHold(1000)
		h.update(0, true, "A"); assertEquals(0.9f, filling(h.update(900, true, "A")).raw, 1e-4f)
		assertEquals(State.Hidden, h.update(950, false, "A"))
		assertEquals(State.Filling(0f, 0f), h.update(1000, true, "A"), "starts from zero again")
		assertEquals(0.25f, filling(h.update(1250, true, "A")).shown, 1e-4f, "measured from the new press")
		assertTrue(h.update(1999, true, "A") is State.Filling, "not yet full after the restart")
		assertEquals(State.Fire, h.update(2000, true, "A"))
	}

	@Test fun anotherItemRestartsAndMovingOffHides() {
		val h = WikiHold(1000, 0)
		h.update(0, true, "A"); h.update(800, true, "A")
		assertEquals(0f, filling(h.update(900, true, "B")).raw, "new item -> new fill")
		assertEquals(State.Hidden, h.update(1000, true, null), "off any item -> hidden")
		assertEquals(0f, filling(h.update(1100, true, "B")).raw)
		assertEquals(State.Fire, h.update(2100, true, "B"))
	}

	@Test fun suppressedUntilReleaseAfterTheOverlayCloses() {
		val h = WikiHold(500, 0)
		h.update(0, true, "A")
		h.suppressUntilRelease()
		assertEquals(State.Hidden, h.update(2000, true, "A"))
		h.update(2100, false, null)
		assertTrue(h.update(2200, true, "A") is State.Filling)
	}

	@Test fun holdTimeSettingIsUsed() {
		val h = WikiHold(300, 0)
		h.update(0, true, "A")
		assertEquals(State.Fire, h.update(300, true, "A"))
		h.holdMs = 5000; h.update(400, false, null); h.update(500, true, "A")
		assertTrue(h.update(3000, true, "A") is State.Filling)
	}

	@Test fun hintBarFollowsTheLinearFill() {
		assertEquals(ItemWikiHint.Lines("J", 0), ItemWikiHint.lines(State.Filling(0f, 0f), "J"))
		assertEquals(12, ItemWikiHint.lines(State.Filling(0.2f, 0.5f), "J")!!.filled)
		assertEquals(ItemWikiHint.SEGMENTS, ItemWikiHint.lines(State.Filling(1f, 1f), "K")!!.filled)
		assertEquals("K", ItemWikiHint.lines(State.Filling(1f, 1f), "K")!!.keyName)
		val h = WikiHold(1200); h.update(0, true, "A")
		assertEquals(6, ItemWikiHint.lines(h.update(300, true, "A"), "J")!!.filled, "25% -> 6/24")
		assertEquals(12, ItemWikiHint.lines(h.update(600, true, "A"), "J")!!.filled, "50% -> 12/24")
		assertEquals(18, ItemWikiHint.lines(h.update(900, true, "A"), "J")!!.filled, "75% -> 18/24")
		assertEquals(0f, WikiHold.progress(-5, 100)); assertEquals(0.5f, WikiHold.progress(50, 100)); assertEquals(1f, WikiHold.progress(300, 100))
	}
}
