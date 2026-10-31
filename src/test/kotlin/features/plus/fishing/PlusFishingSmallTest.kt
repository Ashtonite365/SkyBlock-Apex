package dev.ashtonite365.apex.test.features.plus.fishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.fishing.PlusFishingSmall

class PlusFishingSmallTest {
	@Test
	fun flay() {
		assertTrue(PlusFishingSmall.isFlayParticle(true, 1.0, 1.0, 0.0, 0, 1f))
		assertTrue(PlusFishingSmall.isFlayParticle(true, 0.003921568859368563, 0.003921568859368563, 0.003921568859368563, 0, 1f))
		assertFalse(PlusFishingSmall.isFlayParticle(true, 1.0, 1.0, 0.0, 1, 1f))
		assertFalse(PlusFishingSmall.isFlayParticle(false, 1.0, 1.0, 0.0, 0, 1f))
	}

	@Test
	fun itemLabels() {
		assertEquals("§a§l+ §r§7x3 §rRaw Fish", PlusFishingSmall.itemLabel("Raw Fish", 3, false))
		assertEquals("§c§l- §r§7Fish Bait", PlusFishingSmall.itemLabel("Fish Bait", 1, true))
		assertTrue(PlusFishingSmall.isBaitName("Glowy Chum Bait"))
	}

	@Test
	fun cocoon() {
		assertEquals("Lord Jawbus", PlusFishingSmall.cocoonedMob("CAUGHT! You cocooned a Lord Jawbus!"))
		assertEquals("Ent", PlusFishingSmall.cocoonedMob("CAUGHT! You cocooned an Ent!"))
		assertNull(PlusFishingSmall.cocoonedMob("You caught a Squid!"))
		assertTrue("lord jawbus" in PlusFishingSmall.parseMobList(" Lord Jawbus ,Thunder"))
	}
}
