// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.dungeon.secrets

import dev.ashtonite365.apex.features.dungeon.SecretRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.secrets.SecretWayfinderLogic.Kind
import dev.ashtonite365.apex.features.dungeon.secrets.SecretWayfinderLogic.Secret

class LeverGateTest {
	private val deathmite = LeverGate.catalog["Deathmite"]!!

	@Test fun catalogHasDeathmiteNumber4() {

		assertEquals(listOf(LeverGate.Entry(70, 69, 41 * 41, true)), deathmite)
	}

	@Test fun deathmiteCatalogLeverIsFoundInEveryRotationAndTheBrazierLeverIsNot() {
		val info = SecretRooms.Info("Deathmite", "NORMAL", "1x3", emptyList(), 6, 4)
		for (rot in SecretRooms.Rotation.entries) {
			val room = SecretRooms.Detected(info, rot, -185, -105, emptyList(), 0, "test")
			fun w(x: Int, y: Int, z: Int) = room.toReal(x, y, z).let { LeverGate.P(it.first, it.second, it.third) }
			val lever = w(53, 70, 15); val chest = w(12, 69, 15)
			val brazier = w(30, 84, 10)
			val otherChest = w(83, 92, 27)
			assertEquals(41 * 41, (lever.x - chest.x) * (lever.x - chest.x) + (lever.z - chest.z) * (lever.z - chest.z), "$rot")
			assertEquals(setOf(lever), LeverGate.gating(deathmite, listOf(lever, brazier), listOf(chest, otherChest)), "$rot")
		}
	}

	@Test fun catalogLeverIsTargetedFirstFromTheMomentYouEnter() {
		val chest = Secret(Kind.CHEST, -185.5, 69.5, -110.5, "c"); val lever = Secret(Kind.LEVER, -144.5, 70.5, -110.5, "l")

		assertEquals(lever, SecretWayfinderLogic.pickTarget(listOf(chest, lever), emptySet(), emptySet(), -190.0, 70.0, -110.0))
	}

	@Test fun brazierLeverIsIgnored() {
		assertTrue(LeverGate.isDecorative("cauldron", listOf("air")))
		assertTrue(LeverGate.isDecorative("stone_bricks", listOf("fire", "air")))
		assertTrue(LeverGate.isDecorative("stone_bricks", listOf("campfire")))
		assertFalse(LeverGate.isDecorative("stone_bricks", listOf("air", "cobblestone")))
		val brazier = LeverGate.P(0, 84, 0); val wall = LeverGate.P(10, 70, 0)
		val decor = setOf(brazier)

		assertEquals(wall, LeverGate.backup(listOf(brazier, wall), { it !in decor }, 0.5, 80.0, 0.5))
		assertNull(LeverGate.backup(listOf(brazier), { it !in decor }, 0.5, 80.0, 0.5))

		assertEquals(emptySet<LeverGate.P>(), LeverGate.gating(deathmite, listOf(brazier), listOf(LeverGate.P(0, 69, 41))))
	}

	@Test fun backupLeverMustBeReachable() {
		assertTrue(LeverGate.reachable(LeverGate.P(0, 73, 0), 70.0, ladderBelow = false))
		assertFalse(LeverGate.reachable(LeverGate.P(0, 80, 0), 70.0, ladderBelow = false))
		assertTrue(LeverGate.reachable(LeverGate.P(0, 80, 0), 70.0, ladderBelow = true))
	}
}
