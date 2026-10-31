package dev.ashtonite365.apex.test.features.plus.fishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.features.plus.fishing.PlusTrophyHelpers

class PlusTrophyHelpersTest {
	@Test
	fun geyser() {
		assertTrue(PlusTrophyHelpers.isGeyserParticle(true, 15, 0.05f, 0.1f, 0.6f, 0.1f))
		assertFalse(PlusTrophyHelpers.isGeyserParticle(true, 14, 0.05f, 0.1f, 0.6f, 0.1f))
		val b = PlusTrophyHelpers.geyserBox(Vec3(10.0, 120.0, -5.0))
		assertEquals(8.0, b.minX); assertEquals(12.0, b.maxX); assertEquals(117.9, b.minY, 1e-9)
	}

	@Test
	fun trophyLore() {
		assertTrue(PlusTrophyHelpers.isTrophyLore(listOf("Caught by you", "BRONZE TROPHY FISH")))
		assertFalse(PlusTrophyHelpers.isTrophyLore(listOf("COMMON FISHING ROD")))
	}
}
