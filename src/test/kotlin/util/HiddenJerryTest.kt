// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import java.util.TimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.parsers.JerryParser

class HiddenJerryTest {

	private val real = "  There is a Purple Jerry!"

	@Test fun detectsTheRealSpawnLine() {
		assertEquals(JerryParser.Colour.PURPLE, JerryParser.parse(real))
		assertEquals(JerryParser.Colour.GOLDEN, JerryParser.parse("There is a Golden Jerry!"))
		assertEquals(JerryParser.Colour.GREEN, JerryParser.parse("You found a Green Jerry!"))
		assertNull(JerryParser.parse("                      +2 Purple Jerry ☘ Magic Find"))
		assertNull(JerryParser.parse("Purple Jerry II ➜ III"))
	}

	@Test fun recordsEpochAndFormatsRelative() {
		val d = JerryParser.JerryData(lastJerryMillis = 1_000L)
		val spawn = 1_761_021_498_000L
		assertTrue(JerryParser.apply(d, real, spawn))
		assertEquals(spawn, d.lastJerryMillis)
		assertEquals(1, d.counts[JerryParser.Colour.PURPLE])
		assertFalse(JerryParser.apply(d, "Hello", spawn + 5))
		val tz = TimeZone.getDefault()
		try {
			for (zone in listOf("UTC", "Australia/Sydney", "America/Los_Angeles")) {
				TimeZone.setDefault(TimeZone.getTimeZone(zone))
				assertEquals(65.seconds, JerryParser.timeSinceLast(d, spawn + 65_000), zone)
				assertEquals(3.minutes, JerryParser.timeSinceLast(d, spawn + 180_000), zone)
			}
		} finally { TimeZone.setDefault(tz) }
	}
}
