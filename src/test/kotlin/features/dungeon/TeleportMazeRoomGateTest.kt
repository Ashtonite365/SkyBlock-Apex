package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.TeleportMaze
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Gate
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Pad

class TeleportMazeRoomGateTest {
	private val mazeCells = listOf(1 to 5)
	private val altarCells = listOf(2 to 0, 3 to 0, 3 to 1)
	private val otherMazeCells = listOf(0 to 0)
	private val mazePads = setOf(Pad(-150, 69, -25), Pad(-160, 69, -20), Pad(-160, 69, -14), Pad(-158, 69, -30))

	private val altarPads = setOf(Pad(-90, 70, -186), Pad(-88, 70, -186), Pad(-90, 70, -184), Pad(-88, 70, -184))
	private val inMaze = -153.5 to -25.5
	private val inAltar = -92.0 to -184.0

	private fun Gate.at(name: String?, cells: List<Pair<Int, Int>>?, pos: Pair<Double, Double>, enabled: Boolean = true, dungeon: Boolean = true) =
		update(enabled, dungeon, name, cells, pos.first, pos.second)

	@Test fun noRenderInAnotherRoom() {
		val g = Gate()
		assertNull(g.at("Altar", altarCells, inAltar))
		assertFalse(g.open); assertEquals(Gate.Why.OTHER_ROOM, g.why)
		assertTrue(g.visible(altarPads + mazePads).isEmpty(), "no pad, label or guide outside the maze")
		assertNull(g.at(null, null, inAltar)); assertEquals(Gate.Why.NO_ROOM, g.why)
		assertTrue(g.visible(altarPads).isEmpty())

		assertTrue(g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze)!!.open)
		assertEquals(mazePads, g.visible(mazePads))
		val c = g.at("Altar", altarCells, inAltar)!!
		assertFalse(c.open); assertEquals(Gate.Why.OTHER_ROOM, c.why)
		assertFalse(g.open); assertTrue(g.visible(mazePads + altarPads).isEmpty())

		val g2 = Gate()
		assertNull(g2.at(TeleportMaze.ROOM_NAME, mazeCells, inAltar)); assertEquals(Gate.Why.OUTSIDE_ROOM, g2.why)
		assertTrue(g2.visible(mazePads).isEmpty())
	}

	@Test fun noRenderWhenOffOrOutsideDungeon() {
		val g = Gate()
		assertNull(g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze, enabled = false)); assertFalse(g.open)
		assertNull(g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze, dungeon = false)); assertFalse(g.open)
		assertTrue(g.visible(mazePads).isEmpty())
	}

	@Test fun padsOutsideMazeBoundsIgnored() {
		val b = TeleportMaze.roomBounds(mazeCells)!!
		assertEquals(TeleportMaze.Bounds(-168, 0, -40, -138, 255, -10), b)
		assertEquals(TeleportMaze.dungeonRoom(-150, -25), b)
		val edge = setOf(Pad(-168, 69, -40), Pad(-138, 69, -10))
		val outside = setOf(Pad(-137, 69, -25), Pad(-169, 69, -25), Pad(-150, 69, -9), Pad(-150, 69, -41)) + altarPads
		assertEquals(mazePads + edge, TeleportMaze.padsInside(mazePads + edge + outside, b))
		assertTrue(TeleportMaze.padsInside(mazePads, null).isEmpty())
		val g = Gate(); g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze)
		assertEquals(b, g.room)
		assertEquals(mazePads, g.visible(mazePads + outside))

		assertEquals(TeleportMaze.Bounds(-136, 0, -200, -74, 255, -138), TeleportMaze.roomBounds(altarCells))
		assertNull(TeleportMaze.roomBounds(emptyList()))
	}

	@Test fun leaveParksAndReEnterResumes() {
		val g = Gate()
		val open1 = g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze)!!
		assertTrue(open1.open && open1.discard && !open1.resumed)
		assertNull(g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze))
		val left = g.at("Altar", altarCells, inAltar)!!
		assertFalse(left.open); assertFalse(left.discard)
		assertEquals(TeleportMaze.roomBounds(mazeCells), g.parked)
		assertNull(g.at(null, null, inAltar))
		assertNotNull(g.parked)
		val back = g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze)!!
		assertTrue(back.open && back.resumed && !back.discard)
		assertNull(g.parked)
	}

	@Test fun differentMazeOrLeavingDungeonDiscards() {
		val g = Gate()
		g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze); g.at("Altar", altarCells, inAltar)
		val other = g.at(TeleportMaze.ROOM_NAME, otherMazeCells, -180.0 to -185.0)!!
		assertTrue(other.open && other.discard && !other.resumed)
		val out = g.at(null, null, 0.0 to 0.0, dungeon = false)!!
		assertFalse(out.open); assertTrue(out.discard); assertNull(g.parked)
		val again = g.at(TeleportMaze.ROOM_NAME, otherMazeCells, -180.0 to -185.0)!!
		assertTrue(again.discard && !again.resumed)
		val off = g.at(TeleportMaze.ROOM_NAME, otherMazeCells, -180.0 to -185.0, enabled = false)!!
		assertTrue(off.discard); assertNull(g.parked)
		assertNull(g.at(null, null, 0.0 to 0.0, dungeon = false))
		g.at(TeleportMaze.ROOM_NAME, mazeCells, inMaze); g.reset()
		assertFalse(g.open); assertNull(g.parked); assertNull(g.room)
	}

	@Test fun trackerStateAcrossLeaveAndReEnter() {
		val g = Gate(); val t = TeleportMaze.Tracker()
		fun step(name: String?, cells: List<Pair<Int, Int>>?, pos: Pair<Double, Double>, pad: Pad?, tp: Boolean = false) {
			g.at(name, cells, pos)?.let { if (it.discard) t.reset() }
			if (!g.open) return
			val pads = g.visible(listOfNotNull(pad))
			if (t.room == null && pads.isNotEmpty()) t.setRoomPads(g.visible(mazePads + altarPads), g.room)
			t.tick(pos.first, 70.0, pos.second, pads.firstOrNull(), tp)
		}

		step("Altar", altarCells, inAltar, altarPads.first())
		assertTrue(t.visited.isEmpty() && t.allPads.isEmpty())
		val a = Pad(-150, 69, -25)
		step(TeleportMaze.ROOM_NAME, mazeCells, a.x + 0.5 to a.z + 0.5, a)
		assertEquals(setOf(a), t.visited); assertEquals(mazePads, t.allPads)
		step("Altar", altarCells, inAltar, altarPads.first())
		assertEquals(setOf(a), t.visited)
		step(TeleportMaze.ROOM_NAME, mazeCells, inMaze, null)
		assertEquals(setOf(a), t.visited)
		step(null, null, 0.0 to 0.0, null)
		g.update(true, false, null, null, 0.0, 0.0)?.let { if (it.discard) t.reset() }
		assertTrue(t.visited.isEmpty() && t.allPads.isEmpty())
	}
}
