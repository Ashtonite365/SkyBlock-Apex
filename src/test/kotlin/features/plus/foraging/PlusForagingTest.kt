package dev.ashtonite365.apex.test.features.plus.foraging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.foraging.PlusForaging

class PlusForagingTest {
	@Test
	fun patterns() {
		assertTrue(PlusForaging.isFusionSound("entity.firework_rocket.blast", 20f))
		assertFalse(PlusForaging.isFusionSound("entity.firework_rocket.blast", 1f))
		assertTrue(PlusForaging.isBeaconReady(listOf("Moonglade Beacon:", " Cooldown: AVAILABLE")))
		assertFalse(PlusForaging.isBeaconReady(listOf(" Cooldown: 4m")))
		assertEquals(PlusForaging.Quest.SECOND, PlusForaging.questFor("You must complete the Into the Woods Quest to use this!"))
		assertNull(PlusForaging.questFor("hello"))
	}
}
