// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.events.GreatSpook

class GreatSpookTest {
	@Test
	fun math() {
		assertEquals(80L, GreatSpook.evaluate("(10*2)+12*5"))
		assertEquals(7L, GreatSpook.evaluate("1 + 2 x 3"))
		assertNull(GreatSpook.evaluate("2+"))
		assertEquals(1.minutes, GreatSpook.remaining(5.minutes))
	}
}
