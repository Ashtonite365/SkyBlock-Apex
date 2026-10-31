package dev.ashtonite365.apex.test.features.plus.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.slayer.PlusSlayerLogic

class PlusSlayerLogicTest {
	@Test
	fun progress() {
		assertEquals(1234.0 to 2400.0, PlusSlayerLogic.parseProgress("(1,234/2,400) Combat XP"))
		assertEquals(12000.0 to 15000.0, PlusSlayerLogic.parseProgress("12k/15k Kills"))
		assertNull(PlusSlayerLogic.parseProgress("Slay the boss!"))
		assertEquals(10, PlusSlayerLogic.remainingKills(1000.0, 2000.0, 100.0))
		assertNull(PlusSlayerLogic.remainingKills(1000.0, 2000.0, 0.0))
	}

	@Test
	fun tags() {
		assertTrue(PlusSlayerLogic.isIrrelevantMobTag("[Lv7] Graveyard Zombie 120/120❤"))
		assertFalse(PlusSlayerLogic.isIrrelevantMobTag("☠ Revenant Horror 500/500❤"))
		assertEquals(5, PlusSlayerLogic.firePillarSeconds("5s 8 hits"))
		assertEquals(PlusSlayerLogic.HellionShield.ASHEN, PlusSlayerLogic.HellionShield.inName("ASHEN ♨8"))
		assertTrue(PlusSlayerLogic.isTwinclaws("TWINCLAWS 0.9s"))
		assertTrue(PlusSlayerLogic.rngMeterChat.matches("   RNG Meter - 12,345 Stored XP"))
		assertTrue(PlusSlayerLogic.isNotSpawnable(listOf("Doesn't exist here!")))
	}
}
