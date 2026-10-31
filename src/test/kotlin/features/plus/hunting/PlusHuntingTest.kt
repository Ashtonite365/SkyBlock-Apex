package dev.ashtonite365.apex.test.features.plus.hunting

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.hunting.PlusHunting
import dev.ashtonite365.apex.util.SkyBlockIsland

class PlusHuntingTest {
	@Test
	fun logic() {
		assertTrue(PlusHunting.hideCapsule("CRITTER_CAPSULE", false))
		assertFalse(PlusHunting.hideCapsule("MASTERFUL_CRITTER_CAPSULE", false))
		assertTrue(PlusHunting.hideCapsule("MASTERFUL_CRITTER_CAPSULE", true))
		assertTrue(PlusHunting.isHuntingIsland(SkyBlockIsland.GALATEA))
		assertFalse(PlusHunting.isHuntingIsland(SkyBlockIsland.HUB))
		assertTrue(PlusHunting.isPureReptile("PURE REPTILE! You fused ..."))
	}
}
