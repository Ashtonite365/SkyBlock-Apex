// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.ApexReadWarn

class ApexReadWarnTest {
	@Test fun readWarnOnlyOnRealFailureOncePerWindowDebounced() {
		val w = ApexReadWarn("m", graceMs = 1500, debounceMs = 30_000)
		w.parsed(false, 0, 0)
		assertNull(w.check(1, "T", 5_000))
		w.parsed(false, 5, 6_000)
		assertNull(w.check(1, "T", 7_000))
		assertEquals("[Apex] m: could not read the page 'T' (5 items); leaving the vanilla chest", w.check(1, "T", 7_600))
		assertNull(w.check(1, "T", 9_000))
		w.parsed(false, 5, 10_000)
		assertNull(w.check(2, "T", 12_000))
		w.parsed(false, 5, 40_000)
		assertTrue(w.check(3, "T", 42_000) != null)
		w.parsed(true, 5, 50_000)
		assertNull(w.check(4, "T", 60_000))
	}
}
