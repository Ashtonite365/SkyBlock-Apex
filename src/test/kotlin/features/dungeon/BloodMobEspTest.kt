package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.BloodMobEsp

class BloodMobEspTest {
	@Test
	fun onlyCurrentRoom() {

		assertTrue(BloodMobEsp.sameRoom(-190.0, -190.0, -170.5, -199.9))
		assertFalse(BloodMobEsp.sameRoom(-190.0, -190.0, -168.0, -190.0))
		assertFalse(BloodMobEsp.sameRoom(-190.0, -190.0, -190.0, -201.0))
		assertTrue(BloodMobEsp.sameRoom(-41.2, 7.9, -60.0, 20.0))
	}

	@Test
	fun bloodMobNames() {
		assertTrue(BloodMobEsp.isBloodMob("Revoker 1.2M❤"))
		assertTrue(BloodMobEsp.isBloodMob("[Lv250] Mr. Dead 2M❤"))
		assertFalse(BloodMobEsp.isBloodMob("Revoker"))
		assertFalse(BloodMobEsp.isBloodMob("Crypt Undead 120k❤"))
	}

	@Test
	fun exactNamesOnly() {

		assertFalse(BloodMobEsp.isBloodMob("Walker Zombie 2k❤"))
		assertFalse(BloodMobEsp.isBloodMob("Frosty Skeleton 5k❤"))
		assertFalse(BloodMobEsp.isBloodMob("Skull Collector 10k❤"))
		assertFalse(BloodMobEsp.isBloodMob("Reaper Zombie 40k❤"))
		assertFalse(BloodMobEsp.isBloodMob("Tearful Spider 1k❤"))

		assertTrue(BloodMobEsp.isBloodMob("✯ Walker 2M❤"))
		assertTrue(BloodMobEsp.isBloodMob("[Lv100] Lost Adventurer 125k/250k❤"))
		assertTrue(BloodMobEsp.isBloodMob("Shadow Assassin 2,000❤"))
		assertEquals("Mr. Dead", BloodMobEsp.tagName("[Lv250] Mr. Dead 2M❤"))
		assertNull(BloodMobEsp.tagName("Mr. Dead"))
	}

	@Test
	fun needsTheWatchersRoom() {
		val watcher = -185.0 to -185.0

		assertFalse(BloodMobEsp.inBloodContext(null, -190.0, -190.0, -180.0, -180.0))

		assertTrue(BloodMobEsp.inBloodContext(watcher, -190.0, -190.0, -180.0, -180.0))

		assertFalse(BloodMobEsp.inBloodContext(watcher, -150.0, -190.0, -150.0, -190.0))

		assertFalse(BloodMobEsp.inBloodContext(watcher, -150.0, -190.0, -180.0, -180.0))
	}
}
