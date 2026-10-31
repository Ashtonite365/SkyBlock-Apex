// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.rift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.rift.BerberisSequence
import dev.ashtonite365.apex.features.plus.rift.ButtonRules
import dev.ashtonite365.apex.features.plus.rift.DanceRoom
import dev.ashtonite365.apex.features.plus.rift.Effigies
import dev.ashtonite365.apex.features.plus.rift.EnigmaRules
import dev.ashtonite365.apex.features.plus.rift.ParkourState
import dev.ashtonite365.apex.features.plus.rift.RVec
import dev.ashtonite365.apex.features.plus.rift.RiftRepo
import dev.ashtonite365.apex.util.plus.PlusRepoSource

class PlusRiftRepoLogicTest {
	private fun bundled(path: String) = dev.ashtonite365.apex.Apex.json.parseToJsonElement(PlusRepoSource.readText(path)!!)

	@Test
	fun bundledParkours() {
		for (p in listOf(RiftRepo.RACE, RiftRepo.UPSIDE_DOWN, RiftRepo.LAVA_MAZE, RiftRepo.TUBULATOR)) {
			val d = RiftRepo.parkour(bundled(p))
			assertTrue(d.locations.size > 10, p)
			assertTrue(d.shortCuts.all { it.first in d.locations.indices && it.second in d.locations.indices }, p)
		}
		assertEquals(RVec(-72.0, 70.0, -60.0), RiftRepo.parkour(bundled(RiftRepo.RACE)).locations.first())
	}

	@Test
	fun bundledPuzzles() {
		assertEquals(6, RiftRepo.effigies(bundled(RiftRepo.EFFIGIES)).size)
		assertTrue(RiftRepo.dance(bundled(RiftRepo.DANCE)).size > 20)
		assertEquals(6, RiftRepo.berberis(bundled(RiftRepo.BERBERIS)).size)
		val souls = RiftRepo.enigma(bundled(RiftRepo.ENIGMA))
		assertEquals(RVec(-15.0, 91.0, 94.0), souls["Wyld Woods"]!!["Tough Bark"])
		val spots = RiftRepo.buttons(bundled(RiftRepo.BUTTONS))
		assertEquals(14, spots.size)
		val graph = RiftRepo.graph(bundled(RiftRepo.GRAPH)).filter { "rift_buttons_quest" in it.tags }
		assertTrue(spots.all { s -> graph.any { it.position == s.position && it.name == "${s.house} House" } })
	}

	@Test
	fun parkourState() {
		val locs = (0 until 10).map { RVec(it * 3.0, 0.0, 0.0) }
		val s = ParkourState(locs, listOf(1 to 5, 8 to 20), detectionRange = 1.0)
		s.lookAhead = 3
		assertFalse(s.update(RVec(100.0, 1.0, 0.0), true)); assertFalse(s.shouldRender())
		s.update(RVec(0.5, 1.0, 0.5), true); assertFalse(s.shouldRender())
		s.update(RVec(0.5, 1.0, 0.5), true)
		assertTrue(s.shouldRender()); assertEquals(0, s.current)
		assertEquals(listOf(0, 1, 2), s.visibleSteps().map { it.index })
		assertEquals(listOf(1 to 5), s.visibleShortCuts())
		s.update(RVec(6.5, 1.0, 0.5), false); assertEquals(0, s.current)
		s.update(RVec(6.5, 1.0, 0.5), true); assertEquals(2, s.current)
		assertTrue(s.update(RVec(27.5, 1.0, 0.5), true))
		s.reset(); assertFalse(s.inParkour())
		val ordered = ParkourState(locs, emptyList(), goInOrder = true)
		ordered.update(RVec(0.5, 1.0, 0.5), true); ordered.update(RVec(0.5, 1.0, 0.5), true); ordered.update(RVec(15.5, 1.0, 0.5), true)
		assertEquals(0, ordered.current)
		assertEquals(ParkourState.rainbowRgb(0), ParkourState.rainbowRgb(12))
	}

	@Test
	fun danceRoom() {
		assertEquals("§5Sneak §bJump", DanceRoom.formatInstruction("sneak jump"))
		val l = DanceRoom.lines(listOf("move", "sneak", "jump", "punch", "stand"), 1, 10, 2)
		assertEquals(listOf("§7Now: §5Sneak §f0:500", "§7Next: §bJump", "§7Later: §dPunch"), l)
		assertTrue(DanceRoom.isSuccess("block.note_block.bass", 0.6984127f, 1f))
		assertTrue(DanceRoom.isFailure("entity.player.burp", 1f, 0.8f))
		assertTrue(DanceRoom.inRoom(-263.0, 35.0, -105.0)); assertFalse(DanceRoom.inRoom(0.0, 35.0, -105.0))
		assertEquals(listOf("§cError fetching Dance Room Instructions!"), DanceRoom.lines(emptyList(), 0, 0, 3))
	}

	@Test
	fun effigies() {
		assertEquals(listOf(false, false, false, false, false, true), Effigies.parseHearts("Effigies: §c⧯§c⧯§c⧯§c⧯§c⧯§7⧯"))
		assertNull(Effigies.parseHearts("Purse: 5"))
		assertEquals(899_000L, Effigies.parseDuration("14m59s")); assertEquals(1000L, Effigies.parseDuration("1s"))
		assertTrue(Effigies.timerPattern.matches("§eRespawn §c14m59s §7(or click!)"))
		assertEquals("2m 5s", Effigies.format(125_000))
	}

	@Test
	fun enigmaAndButtons() {
		assertEquals("Woods Flower Pot", EnigmaRules.title.matchEntire("✔ Enigma: Woods Flower Pot")!!.groups["name"]!!.value)
		assertEquals("Wyld Woods", EnigmaRules.guideArea.matchEntire("To Rift Guide ➜ Wyld Woods")!!.groups["area"]!!.value)
		assertTrue(EnigmaRules.found.matches("SOUL! You unlocked an Enigma Soul!"))
		val souls = mapOf("A" to mapOf("x" to RVec(0.0, 0.0, 0.0), "y" to RVec(5.0, 0.0, 0.0)))
		assertEquals("A" to "y", EnigmaRules.closest(souls, RVec(4.0, 0.0, 0.0)))
		assertNull(EnigmaRules.closest(souls, RVec(100.0, 0.0, 0.0)))
		assertTrue(ButtonRules.hit.matches("§eYou have hit §r§b10/56 §r§eof the wooden buttons!"))
		val a = RiftRepo.ButtonSpot("A", RVec(0.0, 0.0, 0.0), listOf(RVec(1.0, 0.0, 0.0)))
		val b = RiftRepo.ButtonSpot("B", RVec(10.0, 0.0, 0.0), listOf(RVec(11.0, 0.0, 0.0)))
		assertEquals(a, ButtonRules.nearestSpot(listOf(a, b), emptySet(), RVec(1.0, 0.0, 0.0)))
		assertEquals(b, ButtonRules.nearestSpot(listOf(a, b), setOf(RVec(1.0, 0.0, 0.0)), RVec(1.0, 0.0, 0.0)))
	}

	@Test
	fun berberis() {
		val s = BerberisSequence(3)
		val p = (0 until 3).map { RVec(it.toDouble(), 0.0, 0.0) }
		assertFalse(s.respawn(p[0], 0)); assertFalse(s.respawn(p[0], 10)); assertFalse(s.respawn(p[1], 20))
		assertTrue(s.respawn(p[2], 30)); assertTrue(s.isRendering)
		assertFalse(s.broke(p[1])); assertTrue(s.broke(p[0])); assertEquals(p[1], s.currentTarget)
		s.broke(p[1]); s.broke(p[2])
		assertTrue(s.isRendering); assertNull(s.currentTarget)
		val t = BerberisSequence(3); t.respawn(p[0], 0); t.respawn(p[1], 5000)
		assertEquals(listOf(p[1]), t.sequence)
		assertNotNull(RVec.parse("-1.5:2:3")); assertNull(RVec.parse("1:2"))
	}
}
