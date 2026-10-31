// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.kuudra

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.devices.SharpshooterHighlight
import dev.ashtonite365.apex.features.kit.kuudra.KuudraKitLogic

class KuudraKitLogicTest {
	@Test
	fun parsers() {
		assertEquals(Triple("Ashton", 3, 6), KuudraKitLogic.supplyPickup("[MVP+] Ashton recovered one of Elle's supplies! (3/6)"))
		assertEquals(45 to 3, KuudraKitLogic.buildProgress("Building Progress 45% (3 Players Helping)"))
		assertEquals(1200, KuudraKitLogic.manaDrain("Used Extreme Focus! (1200 Mana)"))
		assertNull(KuudraKitLogic.manaDrain("nope"))
		assertTrue(KuudraKitLogic.PILE.containsMatchIn("PROGRESS: 20%"))
		assertEquals("Bob", SharpshooterHighlight.deviceCompleter("Bob completed a device! (3/7)"))
	}

	@Test
	fun perks() {
		assertTrue(KuudraKitLogic.uselessPerk("Steady Hands II", false))
		assertTrue(KuudraKitLogic.uselessPerk("Human Cannonball", false))
		assertFalse(KuudraKitLogic.uselessPerk("Human Cannonball", true))
		assertFalse(KuudraKitLogic.uselessPerk("Elle's Lava Rod Upgrade", false))
	}

	@Test
	fun spots() {
		assertEquals(6, KuudraKitLogic.dropSpots.size)
		assertEquals("§a80", KuudraKitLogic.colourBuild(80)); assertEquals("§c1", KuudraKitLogic.colourBuilders(1))
		val p = KuudraKitLogic.giantPickup(0.0, 0.0, -130f)
		assertEquals(3, p.x); assertEquals(0, p.z); assertEquals(73, p.y)
	}
}
