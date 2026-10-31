// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.rift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.rift.MotesOrbTracker
import dev.ashtonite365.apex.features.plus.rift.RiftRules
import dev.ashtonite365.apex.features.plus.rift.SnakeTracker

class PlusRiftLogicTest {
	@Test
	fun puzzles() {
		val names = mapOf(2 to "Alpha", 11 to "Alpha", 3 to "Beta", 21 to "Gamma", 23 to "Beta")
		assertEquals(1, RiftRules.hackingColour(11) { names[it] })
		assertEquals(2, RiftRules.hackingColour(21) { names[it] })
		assertEquals(3, RiftRules.hackingColour(23) { names[it] })
		assertEquals(RiftRules.Terminal.RED, RiftRules.nearestTerminal(-68.0, 65.0, -63.0))
		assertTrue(RiftRules.punched.matches("PUNCHCARD! You punched [MVP+] Steve and both regained +25ф Rift Time!"))
		assertTrue(RiftRules.isRiftGuide(listOf("To Rift Guide")))
		assertTrue(RiftRules.guideNotCompleted(listOf("x", "✖ Not completed yet!")))
		val crux = RiftRules.cruxProgress(listOf("II Shy: 5/10", "IV Volt: MAXED"))
		assertEquals(2, crux.size); assertEquals(75, RiftRules.cruxPercent(crux))
	}

	@Test
	fun livingCave() {
		val t = SnakeTracker()
		t.added(SnakeTracker.P(0, 0, 0), 0); t.added(SnakeTracker.P(1, 0, 0), 1); t.added(SnakeTracker.P(9, 0, 0), 2)
		assertEquals(2, t.snakes.size); assertEquals(SnakeTracker.P(1, 0, 0), t.snakes[0].blocks[0])
		t.removed(SnakeTracker.P(9, 0, 0)); assertEquals(1, t.snakes.size)
		assertEquals("§a" + "|".repeat(10) + "§7" + "|".repeat(10) + "§f", RiftRules.progressBar(0.5))
	}

	@Test
	fun extras() {
		val m = RiftRules.ubikCooldown.matchEntire("SPLIT! You need to wait 1h 2m before you can play again.")!!
		assertEquals(3_720_000L, RiftRules.parseDuration(m.groups["duration"]!!.value))
		assertEquals(12, RiftRules.geckoCombo("[|||||] x12 [||]"))
	}

	@Test
	fun larvaOdonataAgaricus() {
		assertEquals(RiftRules.SkullTarget.WORN, RiftRules.skullTarget("LARVA_HOOK"))
		assertEquals(RiftRules.SkullTarget.HELD, RiftRules.skullTarget("EMPTY_ODONATA_BOTTLE"))
		assertNull(RiftRules.skullTarget("ASPECT_OF_THE_END"))
		assertEquals("§cClick!", RiftRules.agaricusLabel("red_mushroom", 0))
		assertEquals("§e1.5s", RiftRules.agaricusLabel("brown_mushroom", 1500))
	}

	@Test
	fun motesOrb() {
		val t = MotesOrbTracker()
		for (i in 0 until 60) t.particle(0.0, 0.0, 0.0, i * 13L)
		assertEquals(1, t.visible().size)
		t.update(780 + 400); assertEquals(0, t.visible().size)
		assertTrue(MotesOrbTracker.pickupChat.matches("ORB! Picked up +120 Motes, recovered +5ф Rift Time!"))
	}
}
