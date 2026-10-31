package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.TeleportMaze
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Guide
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Pad

class TeleportMazeTest {

	private val a1 = Pad(0, 70, 0); private val a2 = Pad(2, 70, 0)
	private val b1 = Pad(10, 70, 0); private val b2 = Pad(12, 70, 0)
	private val c1 = Pad(20, 70, 0); private val c2 = Pad(22, 70, 0)
	private val all = setOf(a1, a2, b1, b2, c1, c2)

	private fun tracker() = TeleportMaze.Tracker().apply { setRoomPads(all) }
	private fun at(p: Pad) = Triple(p.x + 0.5, p.y + 1.0, p.z + 0.5)
	private fun TeleportMaze.Tracker.stand(p: Pad?, x: Double, z: Double, tp: Boolean) = tick(x, 71.0, z, p, tp)

	@Test fun stepOnAndArrivalRecordedWithLink() {
		val t = tracker()
		t.stand(a1, 0.5, 0.5, false)
		assertEquals(setOf(a1), t.visited)
		t.stand(b1, 10.5, 0.5, true)
		assertEquals(setOf(a1, b1), t.visited)
		assertEquals(mapOf(a1 to b1, b1 to a1), t.links)
	}

	@Test fun dedupe() {
		val t = tracker()
		repeat(3) { t.stand(a1, 0.5, 0.5, false) }
		t.stand(null, 1.5, 0.5, false); t.stand(a1, 0.5, 0.5, false)
		assertEquals(1, t.visited.size)
		assertTrue(t.links.isEmpty())
	}

	@Test fun resetOnRoomLeave() {
		val t = tracker()
		t.stand(a1, 0.5, 0.5, false)
		assertTrue(t.stand(null, 25.0, 0.5, false))
		assertFalse(t.stand(null, 40.0, 0.5, false))
		assertTrue(t.visited.isEmpty() && t.links.isEmpty() && t.room == null)
	}

	@Test fun resetOnDungeonEndAndWorldChange() {

		val t = tracker(); t.stand(a1, 0.5, 0.5, false); t.stand(b1, 10.5, 0.5, true)
		t.reset()
		assertTrue(t.visited.isEmpty() && t.links.isEmpty() && t.allPads.isEmpty())
	}

	@Test fun cellsGroupClosePads() {
		val c = TeleportMaze.cells(all)
		assertEquals(c[a1], c[a2]); assertEquals(c[b1], c[b2]); assertEquals(c[c1], c[c2])
		assertNotEquals(c[a1], c[b1]); assertNotEquals(c[b1], c[c1])
	}

	@Test fun unlitInOwnCellIsTargetedDirectly() {
		val g = TeleportMaze.guide(all, setOf(a1), emptyMap(), a1)
		assertEquals(Guide.Step(a2, a2, 0, 2.0), g)
	}

	@Test fun bfsPathThroughKnownLinks() {

		val visited = setOf(a1, a2, b1, b2, c1)
		val links = mapOf(a1 to b1, b2 to c1)
		val g = TeleportMaze.guide(all, visited, links, a2) as Guide.Step
		assertEquals(a1, g.next); assertEquals(c2, g.target); assertEquals(2, g.teleports)
	}

	@Test fun nearestTargetFewestTeleportsThenWalk() {
		val far = Pad(14, 70, 3)
		val pads = all + far
		val g = TeleportMaze.guide(pads, setOf(a1, a2, b1, c1, c2), mapOf(a1 to b1, a2 to c1), a1) as Guide.Step
		assertEquals(b2, g.target); assertEquals(1, g.teleports); assertEquals(a1, g.next)
	}

	@Test fun alwaysAVisibleTarget() {

		val links = mapOf(a1 to Pad(100, 70, 100), a2 to Pad(101, 70, 100))
		val g = TeleportMaze.guide(all, setOf(a1, a2), links, a1) as Guide.Step
		assertEquals(Guide.Why.UNKNOWN_ELSEWHERE, g.why); assertEquals(b1, g.target)

		val allKnown = mapOf(a1 to b1, b1 to a1, a2 to c1, c1 to a2, b2 to c2, c2 to b2)
		assertTrue(TeleportMaze.guide(all, all, allKnown, a1) is Guide.Step)
		assertEquals(Guide.Nothing, TeleportMaze.guide(emptySet(), emptySet(), emptyMap(), a1))
	}

	@Test fun guideUpdatesAfterTeleport() {
		val t = tracker()
		t.stand(a1, 0.5, 0.5, false); t.stand(a2, 2.5, 0.5, false)

		val g0 = TeleportMaze.guide(t.allPads, t.visited, t.links, a2) as Guide.Step
		assertEquals(Guide.Why.UNKNOWN_ELSEWHERE, g0.why); assertEquals(-1, g0.teleports)
		t.stand(a2, 2.5, 0.5, false); t.stand(b1, 10.5, 0.5, true)
		val g = TeleportMaze.guide(t.allPads, t.visited, t.links, b1) as Guide.Step
		assertEquals(b2, g.target); assertEquals(0, g.teleports)
	}

	private val cx = -168; private val cz = -136
	private fun r(x: Int, z: Int) = Pad(cx + x, 69, cz + z)
	private val kit = listOf(
		r(4, 12), r(4, 6), r(10, 12), r(10, 6), r(4, 20), r(4, 14), r(10, 20), r(10, 14),
		r(4, 28), r(4, 22), r(10, 28), r(10, 22), r(12, 28), r(12, 22), r(18, 28), r(18, 22),
		r(20, 28), r(20, 22), r(26, 28), r(26, 22), r(26, 20), r(26, 14), r(20, 20), r(20, 14),
		r(26, 12), r(26, 6), r(20, 12), r(20, 6), r(15, 14), r(15, 12),
	)
	private val kitSet = kit.toSet()
	private val room = TeleportMaze.dungeonRoom(cx + 5, cz + 5)
	private fun mazeTracker() = TeleportMaze.Tracker().apply { setRoomPads(kitSet, room) }

	private fun TeleportMaze.Tracker.landBy(p: Pad, yaw: Float = 0f) = tick(p.x + 1.5, 69.5, p.z + 0.5, null, true, yaw)
	private fun TeleportMaze.Tracker.standOn(p: Pad) = tick(p.x + 0.5, 69.8125, p.z + 0.5, p, false)

	@Test fun dungeonRoomIsTheGridCellFullHeight() {
		assertEquals(TeleportMaze.Bounds(cx, 0, cz, cx + 30, 255, cz + 30), room)
	}

	@Test fun gridCellsAndStartHub() {
		val c = TeleportMaze.cells(kitSet)
		assertEquals(setOf(r(15, 14), r(15, 12)), TeleportMaze.startPads(kitSet))
		assertEquals(TeleportMaze.HUB_CELL, c[r(15, 14)])

		assertEquals(c[r(4, 12)], c[r(10, 6)]); assertNotEquals(c[r(4, 12)], c[r(4, 14)])
		assertEquals(8, c.values.toSet().size)
	}

	@Test fun startHubFoundWhenRotated() {
		val rot = kit.map { Pad(cx + 30 - (it.z - cz), 69, cz + (it.x - cx)) }.toSet()
		assertEquals(setOf(Pad(cx + 30 - 14, 69, cz + 15), Pad(cx + 30 - 12, 69, cz + 15)), TeleportMaze.startPads(rot))
	}

	@Test fun noStartHubForUnknownLayout() {
		assertTrue(TeleportMaze.startPads(all).isEmpty())
	}

	@Test fun oneTeleportMarksBothPadsKnown() {
		val t = mazeTracker()
		val a = r(4, 6); val b = r(20, 14)
		t.standOn(a); t.landBy(b)
		assertEquals(mapOf(a to b, b to a), t.links)
		assertEquals(setOf(a), t.departed); assertEquals(setOf(b), t.landed)
		assertEquals(TeleportMaze.Teleport(a, b, false), t.lastTeleport)
		val st = TeleportMaze.states(t.allPads, t.links, t.exits, t.firstPad, t.likely)
		assertEquals(TeleportMaze.State.BACK_START, st[b])
		assertEquals(TeleportMaze.State.KNOWN, st[a])
		assertTrue(st.filterKeys { it != a && it != b }.values.none { it == TeleportMaze.State.KNOWN })
	}

	@Test fun arrivalBesidePadIsDetected() {

		val t = mazeTracker()
		t.standOn(r(4, 6)); t.tick(cx + 21.5, 69.5, cz + 15.5, null, true, 0f)
		assertEquals(r(20, 14), t.links[r(4, 6)])
	}

	@Test fun exitDetection() {

		val t = TeleportMaze.Tracker().apply { setRoomPads(kitSet, this@TeleportMazeTest.room) }
		val a = r(10, 20)
		t.standOn(a)
		assertTrue(t.tick(cx + 15.5, 69.5, cz + 1.5, null, true, 0f))
		assertEquals(setOf(a), t.exits); assertTrue(t.links.isEmpty())
		assertEquals(TeleportMaze.Teleport(a, null, true), t.lastTeleport)
		assertTrue(t.isExitLanding(cx + 15.5, 69.5, cz + 1.5)); assertFalse(t.isExitLanding(cx + 5.5, 69.5, cz + 6.5))
		assertEquals(TeleportMaze.State.LEAVE_EXIT, TeleportMaze.states(t.allPads, t.links, t.exits, t.firstPad, null)[a])

		val g = TeleportMaze.guide(t.allPads, t.visited, t.links, r(10, 20), t.exits, null, t.firstPad) as Guide.Step
		assertNotEquals(a, g.next); assertNotEquals(a, g.target)

		assertFalse(t.tick(cx + 40.0, 69.5, cz + 5.0, null, true, 0f)); assertTrue(t.links.isEmpty() && t.exits.isEmpty())
	}

	@Test fun middlePadsAreStartNotFinalRoom() {

		val t = mazeTracker()
		val hub = r(15, 12); val b = r(20, 14)
		t.standOn(hub); t.landBy(b)
		val st = TeleportMaze.states(t.allPads, t.links, t.exits, t.firstPad, t.likely, t.previous)
		assertEquals(TeleportMaze.State.START, st[hub]); assertEquals(TeleportMaze.State.START, st[r(15, 14)])
		assertEquals(TeleportMaze.State.BACK_START, st[b])

		val g = TeleportMaze.guide(t.allPads, t.visited, t.links, b, t.exits, t.likely, t.firstPad, null, t.previous) as Guide.Step
		assertNotEquals(b, g.next); assertEquals(0, g.teleports)
	}

	@Test fun deadEndCellMarkedLeave() {

		val x = listOf(r(4, 20), r(4, 14), r(10, 20), r(10, 14))
		val links = mapOf(
			r(4, 20) to r(26, 6), r(26, 6) to r(4, 20), r(4, 14) to r(26, 12), r(26, 12) to r(4, 14),
			r(10, 20) to r(20, 6), r(20, 6) to r(10, 20), r(10, 14) to r(20, 12), r(20, 12) to r(10, 14),
		)
		val st = TeleportMaze.states(kitSet, links, emptySet(), null, null)
		assertEquals(TeleportMaze.State.LEAVE_DEAD_END, st[r(26, 6)])
		assertTrue(x.all { st[it] == TeleportMaze.State.LEAVE_DEAD_END })
	}

	@Test fun kitRayHeuristicPicksFacedPad() {

		assertTrue(TeleportMaze.rayHits(r(20, 6), cx + 5.5, cz + 6.5, -90f, 0f))
		assertFalse(TeleportMaze.rayHits(r(20, 28), cx + 5.5, cz + 6.5, -90f, 0f))
		val t = mazeTracker()
		t.standOn(r(4, 28)); t.tick(cx + 3.5, 69.5, cz + 6.5, null, true, -90f)
		assertTrue(t.likely!!.contains(r(20, 6)) && t.likely!!.contains(r(26, 6)))
		assertFalse(t.likely!!.contains(r(10, 12)))

		t.standOn(r(10, 6)); t.tick(cx + 21.5, 69.5, cz + 22.5, null, true, 180f)
		assertEquals(setOf(r(20, 6)), t.likely)

		val g = TeleportMaze.guide(t.allPads, t.visited, t.links, r(20, 22), t.exits, t.likely, t.firstPad, t.lastYaw) as Guide.Step
		assertEquals(Guide.Why.UNKNOWN, g.why); assertEquals(r(26, 22), g.target); assertEquals(0, g.teleports)

		val g2 = TeleportMaze.guide(t.allPads, t.visited, t.links, r(20, 22), t.exits, setOf(r(26, 28)), t.firstPad, t.lastYaw) as Guide.Step
		assertEquals(Guide.Why.LIKELY, g2.why); assertEquals(r(26, 28), g2.target)
	}

	@Test fun kitYawFallback() {
		assertTrue(TeleportMaze.yawDiff(r(20, 6), cx + 5.5, cz + 6.5, -90f) < 1.0)
		assertTrue(TeleportMaze.yawDiff(r(4, 28), cx + 5.5, cz + 6.5, -90f) > 45.0)
	}

	@Test fun pairsNumberedColouredAndDirectional() {
		val a = r(4, 6); val b = r(20, 14); val c = r(26, 20); val d = r(15, 12)
		val links = linkedMapOf(a to b, b to a, c to d, d to c)
		val observed = setOf(a to b, d to c, c to d)
		val p = TeleportMaze.pairs(links, observed)
		assertEquals(2, p.size)
		assertEquals(TeleportMaze.PadPair(a, b, 1, TeleportMaze.PALETTE[0], true, false), p[0])
		assertEquals(2, p[1].number); assertEquals(TeleportMaze.PALETTE[1], p[1].colour); assertTrue(p[1].bothTravelled)
		assertFalse(p[0].bothTravelled)

		val q = TeleportMaze.pairs(linkedMapOf(b to a, a to b), setOf(a to b))
		assertEquals(a, q[0].a); assertTrue(q[0].aToB); assertFalse(q[0].bToA)
	}

	@Test fun paletteCyclesAndIsDistinct() {
		val n = TeleportMaze.PALETTE.size
		assertEquals(n, TeleportMaze.PALETTE.toSet().size)
		assertEquals(TeleportMaze.pairColour(1), TeleportMaze.pairColour(n + 1))
		assertNotEquals(TeleportMaze.pairColour(1), TeleportMaze.pairColour(2))
	}

	@Test fun pairsFromTrackerAfterTeleports() {
		val t = mazeTracker()
		t.standOn(r(4, 6)); t.landBy(r(20, 14)); t.standOn(r(26, 20)); t.landBy(r(15, 12))
		val p = TeleportMaze.pairs(t.links, t.observed)
		assertEquals(listOf(1 to setOf(r(4, 6), r(20, 14)), 2 to setOf(r(26, 20), r(15, 12))), p.map { it.number to setOf(it.a, it.b) })
	}

	@Test fun rescanUnionsPads() {
		val t = TeleportMaze.Tracker(); t.setRoomPads(setOf(a1), room); t.setRoomPads(setOf(b1), room)
		assertEquals(setOf(a1, b1), t.allPads); assertEquals(room, t.room)
	}
}
