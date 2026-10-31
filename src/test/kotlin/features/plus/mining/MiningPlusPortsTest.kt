// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.mining

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.mining.MiningPlusPorts

class MiningPlusPortsTest {
	@Test
	fun ghost() {
		assertEquals(MiningPlusPorts.GhostDrop("Sorrow", 278), MiningPlusPorts.parseGhostMf("RARE DROP! Sorrow (+278% ✯ Magic Find)"))
		assertEquals(681, MiningPlusPorts.parseKillCombo("Your Kill Combo has expired! You reached a 681 Kill Combo!"))
		assertNull(MiningPlusPorts.parseKillCombo("nope"))
	}

	@Test
	fun corpseAndNotify() {
		assertEquals("TUNGSTEN", MiningPlusPorts.corpseType("  TUNGSTEN CORPSE LOOT! "))
		assertEquals("§6Golden Goblin", MiningPlusPorts.notification("A Golden Goblin has spawned!"))
		assertNull(MiningPlusPorts.notification("hello"))
	}

	@Test
	fun loot() {
		assertEquals("Tusk Fossil" to 1, MiningPlusPorts.parseLootLine("    Tusk Fossil"))
		assertEquals("Fossil Dust" to 1200, MiningPlusPorts.parseLootLine("    +1,200 Fossil Dust"))
		assertEquals("Jaderald" to 2, MiningPlusPorts.parseLootLine("    Jaderald x2"))
		assertNull(MiningPlusPorts.parseLootLine("No indent"))
	}
}
