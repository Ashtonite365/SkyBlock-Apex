// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenPestTimers
import dev.ashtonite365.apex.features.garden.GardenPestTimers.Cooldown

class GardenPestTimersTest {
	@Test
	fun cooldown() {
		assertEquals(Cooldown.Ready, GardenPestTimers.parseCooldown(listOf("Pests:", " Cooldown: READY")))
		assertEquals(Cooldown.MaxPests, GardenPestTimers.parseCooldown(listOf(" Cooldown: MAX PESTS")))
		assertEquals(Cooldown.MaxPests, GardenPestTimers.parseCooldown(listOf(" Alive: 8", " Cooldown: 1m")))
		assertEquals(Cooldown.Time("1m 58s"), GardenPestTimers.parseCooldown(listOf(" Alive: 3", " Cooldown: 1m 58s")))
		assertEquals(Cooldown.Time("58s"), GardenPestTimers.parseCooldown(listOf(" Cooldown: 58s")))
		assertEquals(Cooldown.Time("1m"), GardenPestTimers.parseCooldown(listOf(" Cooldown: 1m")))
		assertNull(GardenPestTimers.parseCooldown(listOf("Area: Garden")))
	}

	@Test
	fun bonus() {
		assertEquals(0 to null, GardenPestTimers.parseBonus(listOf(" Bonus: INACTIVE")))
		assertEquals(200 to "29m", GardenPestTimers.parseBonus(listOf(" Bonus: +200☘ 29m")))
		assertEquals(200 to "5m 2s", GardenPestTimers.parseBonus(listOf(" Bonus: +200☘ 5m 2s")))
		assertEquals(200 to "8s", GardenPestTimers.parseBonus(listOf(" Bonus: +200 8s")))
		assertNull(GardenPestTimers.parseBonus(listOf(" Cooldown: READY")))
		assertEquals(70.0, GardenPestTimers.parseBonusPestChance(listOf(" Bonus Pest Chance: ൠ70")))
		assertEquals(100.0, GardenPestTimers.parseBonusPestChance(listOf(" Bonus Pest Chance: 100")))
		assertTrue(GardenPestTimers.isBurrowingSpores("VERY RARE CROP! Burrowing Spores"))
		assertFalse(GardenPestTimers.isBurrowingSpores("RARE CROP! Cropie"))
	}

	@Test
	fun tabTime() {
		val now = 1_000_000L
		assertEquals(now + 118_000, GardenPestTimers.tabEndTime("1m 58s", now, null))
		assertEquals(now + 120_000, GardenPestTimers.tabEndTime("1m", now, null))
		assertEquals(now + 100_000, GardenPestTimers.tabEndTime("1m", now, now + 100_000))
		assertEquals(now + 58_000, GardenPestTimers.tabEndTime("58s", now, now + 200_000))
		assertNull(GardenPestTimers.tabEndTime("soon", now, null))
		assertEquals("1m 58s", GardenPestTimers.format(118_000))
		assertEquals("8s", GardenPestTimers.format(7_200))
	}
}
