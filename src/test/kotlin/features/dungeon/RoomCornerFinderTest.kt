package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder.Kind
import dev.ashtonite365.apex.features.dungeon.RoomCornerFinder.Result
import dev.ashtonite365.apex.features.dungeon.RoomCornerLog

class RoomCornerFinderTest {
	private class World {
		val blocks = HashMap<Triple<Int, Int, Int>, Kind>()
		operator fun set(x: Int, y: Int, z: Int, k: Kind) { blocks[Triple(x, y, z)] = k }
		fun kind(x: Int, y: Int, z: Int) = blocks[Triple(x, y, z)] ?: Kind.AIR
	}

	private val c = RoomCornerFinder.centre(0)

	private fun room(roof: Int = 100, marker: Pair<Int, Int> = -15 to -15, markerY: Int = roof, cellX: Int = 0) = World().apply {
		val cx = RoomCornerFinder.centre(cellX)
		this[cx, roof, c] = Kind.OTHER
		this[cx + marker.first, markerY, c + marker.second] = Kind.BLUE_TERRACOTTA
	}

	private fun find(w: World, heightmap: Int?, px: Int = c, pz: Int = c) =
		RoomCornerFinder.find(px, pz, w::kind) { _, _ -> heightmap }

	private fun ok(r: Result) = (r as? Result.Ok)?.found ?: error("expected a corner, got $r")

	@Test fun exactHeightmapKeepsOldBehaviour() {
		val f = ok(find(room(), 100))
		assertEquals(Triple(c - 15, 100, c - 15), Triple(f.x, f.y, f.z))
		assertEquals(0, f.rotation)
	}

	@Test fun rotationFromWhichCorner() {
		assertEquals(90, ok(find(room(marker = 15 to -15), 100)).rotation)
		assertEquals(180, ok(find(room(marker = 15 to 15), 100)).rotation)
		assertEquals(270, ok(find(room(marker = -15 to 15), 100)).rotation)
	}

	@Test fun offByOneHeightmap() {
		assertEquals(100, ok(find(room(), 101)).y)
		assertEquals(100, ok(find(room(), 99)).y)
		assertEquals(100, ok(find(room(), null)).y)
	}

	@Test fun blockAboveTheRoof() {
		val w = room().apply { this[c, 101, c] = Kind.OTHER }
		val f = ok(find(w, 101))
		assertEquals(Triple(c - 15, 100, c - 15), Triple(f.x, f.y, f.z))
	}

	@Test fun goldBlocksAreSkipped() {
		val w = room().apply { this[c, 101, c] = Kind.GOLD; this[c, 102, c] = Kind.GOLD }
		assertEquals(100, RoomCornerFinder.scanRoof(c, c, w::kind))
		assertEquals(100, ok(find(w, 102)).y)
	}

	@Test fun markerTooFarFromRoofIsMissing() {
		val y = 100 - RoomCornerFinder.Y_TOLERANCE - 1

		val r = find(room(markerY = y).apply { this[c - 15, y + 1, c - 15] = Kind.OTHER }, 100)
		assertTrue(r is Result.Missing && "no blue terracotta" in r.reason, "$r")

		assertEquals(y, ok(find(room(markerY = y), 100)).y)
	}

	@Test fun junkHighAboveTheRoomCentre() {

		val w = room().apply { this[c, 146, c] = Kind.OTHER }
		val f = ok(find(w, 255))
		assertEquals(Triple(c - 15, 100, c - 15), Triple(f.x, f.y, f.z))

		w[c + 15, 100, c + 15] = Kind.BLUE_TERRACOTTA
		assertTrue(find(w, 255) is Result.Missing)
	}

	@Test fun outsideTheGridIsReported() {
		val r = find(room(), 100, px = RoomCornerFinder.centre(6))
		assertTrue(r is Result.Missing && "outside the dungeon room grid" in r.reason, "$r")
		assertTrue(find(room(), 100, px = -201) is Result.Missing)
	}

	private fun gapX(i: Int) = -200 + 32 * i - 1

	@Test fun longRoomMarkerThreeCellsAway() {

		val w = room()
		for (i in 1..3) { w[RoomCornerFinder.centre(i), 100, c] = Kind.OTHER; w[gapX(i), 100, c] = Kind.OTHER }
		val px = RoomCornerFinder.centre(3)
		assertEquals(c - 15, ok(find(w, 100, px = px)).x)
		assertEquals(c - 15, ok(find(w, null, px = px)).x)
		assertEquals(listOf(3 to 0, 2 to 0, 1 to 0, 0 to 0), RoomCornerFinder.roomCells(3, 0, { x, _ -> if (x >= -200) 100 else null }, w::kind))
	}

	@Test fun roofDecorationAtANeighbourCentre() {

		val w = room()
		val px = RoomCornerFinder.centre(1)
		w[px, 102, c] = Kind.OTHER; w[gapX(1), 100, c] = Kind.OTHER
		assertEquals(c - 15, ok(find(w, null, px = px)).x)

		val far = room().apply { this[px, 100 + RoomCornerFinder.ROOF_TOLERANCE + 1, c] = Kind.OTHER; this[gapX(1), 100, c] = Kind.OTHER }
		assertEquals(listOf(1 to 0), RoomCornerFinder.roomCells(1, 0, { x, z -> RoomCornerFinder.scanRoof(x, z, far::kind) }, far::kind))
	}

	@Test fun separateRoomWithTheSameRoofIsNotUsed() {

		val w = room().apply { this[RoomCornerFinder.centre(1), 100, c] = Kind.OTHER }
		val px = RoomCornerFinder.centre(1)
		assertTrue(find(w, 100, px = px) is Result.Missing)
		assertFalse(RoomCornerFinder.gapClosed(0 to 0, 1 to 0, 100, w::kind))
		w[gapX(1), 99, c] = Kind.OTHER
		assertTrue(RoomCornerFinder.gapClosed(0 to 0, 1 to 0, 100, w::kind))
		assertTrue(RoomCornerFinder.gapClosed(1 to 0, 0 to 0, 100, w::kind))
	}

	@Test fun aSecondMarkerIsAnotherRoom() {

		val w = room()
		val c1 = RoomCornerFinder.centre(1)
		w[c1, 100, c] = Kind.OTHER; w[gapX(1), 100, c] = Kind.OTHER
		w[c1 + 15, 100, c + 15] = Kind.BLUE_TERRACOTTA
		val roof = { x: Int, z: Int -> RoomCornerFinder.scanRoof(x, z, w::kind) }
		assertTrue(RoomCornerFinder.cellHasMarker(0 to 0, w::kind))
		assertTrue(RoomCornerFinder.cellHasMarker(1 to 0, w::kind))
		assertEquals(listOf(1 to 0), RoomCornerFinder.roomCells(1, 0, roof, w::kind))
		assertEquals(listOf(0 to 0), RoomCornerFinder.roomCells(0, 0, roof, w::kind))

		val f = ok(find(w, 100, px = c1))
		assertEquals(Triple(c1 + 15, 100, c + 15), Triple(f.x, f.y, f.z))

		w[c1 + 15, 100, c + 15] = Kind.AIR
		assertEquals(listOf(1 to 0, 0 to 0), RoomCornerFinder.roomCells(1, 0, roof, w::kind))
	}

	@Test fun trackerResultLine() {
		assertEquals("[ApexRoom] unknown room cells=1,1 0,1 cores=123@corner-168,-168 waiting for chunks at [(0, 1)]",
			dev.ashtonite365.apex.features.dungeon.DungeonRoomTracker.logLine(listOf(1 to 1, 0 to 1),
				dev.ashtonite365.apex.features.dungeon.SecretRooms.Result.Unknown(listOf((1 to 1) to 123), listOf(0 to 1))))
	}

	@Test fun emptyColumnIsMissing() {
		val r = find(World(), null)
		assertTrue(r is Result.Missing && "no roof" in r.reason, "$r")
		assertNull(RoomCornerFinder.scanRoof(c, c, World()::kind))
	}

	@Test fun neighbourCellOfTheSameRoom() {

		val w = room().apply { this[RoomCornerFinder.centre(1), 100, c] = Kind.OTHER; this[c + 16, 100, c] = Kind.OTHER }
		val px = RoomCornerFinder.centre(1)
		assertEquals(c - 15, ok(find(w, 100, px = px)).x)
		assertEquals(c - 15, ok(find(w, 101, px = px)).x)

		val other = room().apply { this[px, 90, c] = Kind.OTHER }
		assertTrue(RoomCornerFinder.find(px, c, other::kind) { x, _ -> if (x == px) 90 else 100 } is Result.Missing)
	}

	@Test fun logOncePerSolverPerRoom() {
		val lines = mutableListOf<String>()
		val log = RoomCornerLog { lines += it }
		assertTrue(log.missing("Boulder", 1, 2, "why"))
		assertFalse(log.missing("Boulder", 1, 2, "why"))
		assertFalse(log.missing("Boulder", 1, 2, "other reason"))
		assertTrue(log.missing("Water Board", 1, 2, "why"))
		assertTrue(log.missing("Boulder", 1, 3, "why"))
		assertEquals(listOf(
			"[Apex] Boulder: room corner not found (why)",
			"[Apex] Water Board: room corner not found (why)",
			"[Apex] Boulder: room corner not found (why)",
		), lines)
		log.clear()
		assertTrue(log.missing("Boulder", 1, 2, "why"))
		assertEquals(4, lines.size)
	}

	@Test fun logIsCappedPerWorld() {
		val lines = mutableListOf<String>()
		val log = RoomCornerLog { lines += it }
		repeat(RoomCornerLog.MAX_PER_WORLD + 10) { log.missing("Boulder", it, 0, "r") }
		assertEquals(RoomCornerLog.MAX_PER_WORLD, lines.size)
	}
}
