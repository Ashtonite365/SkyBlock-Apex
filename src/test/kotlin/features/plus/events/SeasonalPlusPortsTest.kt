// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.events.SeasonalPlusPorts

class SeasonalPlusPortsTest {
	@Test
	fun patterns() {
		assertEquals("§l§6TRICK OR TREAT CHEST!", SeasonalPlusPorts.spookyChestTitle("SPOOKY! A Trick or Treat Chest has appeared!", false))
		assertEquals("§l§cPARTY CHEST", SeasonalPlusPorts.spookyChestTitle("PARTY! A Party Chest has appeared!", true))
		assertNull(SeasonalPlusPorts.spookyChestTitle("hello", true))
		assertTrue(SeasonalPlusPorts.isJyrreMessage("You consumed a Refined Bottle of Jyrre and gained +300✎ Intelligence for 60m!"))
		assertTrue(SeasonalPlusPorts.isReindrakeSpawn("WOAH! [MVP+] Foo summoned TWO Reindrakes from the depths!"))
		assertFalse(SeasonalPlusPorts.isReindrakeSpawn("Reindrake"))
		assertEquals(50.minutes, SeasonalPlusPorts.jyrreRemaining(10.minutes))
		assertEquals(Duration.ZERO, SeasonalPlusPorts.jyrreRemaining(70.minutes))
		assertTrue(SeasonalPlusPorts.isCakeTime(listOf("Late Winter 31st", " New Year Event! 12:30")))
		assertFalse(SeasonalPlusPorts.isCakeTime(listOf("Spring 1st")))
		assertTrue(SeasonalPlusPorts.isTreasureStand(1, false))
		assertFalse(SeasonalPlusPorts.isTreasureStand(1, true))
	}
}
