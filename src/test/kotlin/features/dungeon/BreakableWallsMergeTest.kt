// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.BreakableWalls
import dev.ashtonite365.apex.features.dungeon.BreakableWalls.Face
import dev.ashtonite365.apex.features.dungeon.BreakableWalls.Segment

class BreakableWallsMergeTest {
	private fun p(x: Int, y: Int, z: Int) = Triple(x, y, z)
	private fun s(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>) = Segment(a, b)

	private fun boxEdges(x0: Int, y0: Int, z0: Int, x1: Int, y1: Int, z1: Int): Set<Segment> {
		val out = HashSet<Segment>()
		for (y in listOf(y0, y1)) for (z in listOf(z0, z1)) out += s(p(x0, y, z), p(x1, y, z))
		for (x in listOf(x0, x1)) for (z in listOf(z0, z1)) out += s(p(x, y0, z), p(x, y1, z))
		for (x in listOf(x0, x1)) for (y in listOf(y0, y1)) out += s(p(x, y, z0), p(x, y, z1))
		return out
	}

	@Test
	fun singleBlockIsABox() {
		val m = BreakableWalls.mesh(setOf(p(5, 70, -3)))
		assertEquals((0 until 6).map { Face(5, 70, -3, it) }.toSet(), m.faces.toSet())
		assertEquals(6, m.faces.size)
		assertEquals(boxEdges(5, 70, -3, 6, 71, -2), m.edges.toSet())
		assertEquals(12, m.edges.size)
	}

	@Test
	fun threeByTwoWallIsOneBox() {
		val wall = (0 until 3).flatMap { x -> (0 until 2).map { y -> p(x, y, 0) } }.toSet()
		val m = BreakableWalls.mesh(wall)

		assertEquals(22, m.faces.size)
		assertFalse(Face(0, 0, 0, 0) in m.faces)
		assertFalse(Face(0, 0, 0, 2) in m.faces)
		assertTrue(Face(0, 0, 0, 1) in m.faces && Face(2, 1, 0, 2) in m.faces)

		assertEquals(boxEdges(0, 0, 0, 3, 2, 1), m.edges.toSet())
		assertEquals(12, m.edges.size)
	}

	@Test
	fun lShapeKeepsTheInnerFoldButNotTheSeams() {
		val l = setOf(p(0, 0, 0), p(1, 0, 0), p(0, 1, 0))
		val m = BreakableWalls.mesh(l)
		assertEquals(14, m.faces.size)

		assertEquals(18, m.edges.size)
		assertTrue(s(p(0, 0, 0), p(2, 0, 0)) in m.edges)
		assertTrue(s(p(0, 0, 0), p(0, 2, 0)) in m.edges)
		assertTrue(s(p(1, 1, 0), p(1, 1, 1)) in m.edges)
		assertTrue(s(p(1, 1, 0), p(2, 1, 0)) in m.edges && s(p(1, 1, 0), p(1, 2, 0)) in m.edges)
		assertFalse(s(p(1, 0, 0), p(1, 1, 0)) in m.edges)
		assertFalse(s(p(0, 1, 0), p(1, 1, 0)) in m.edges)
		assertFalse(s(p(1, 0, 0), p(1, 0, 1)) in m.edges)

		for (e in m.edges) {
			val d = listOf(e.to.first - e.from.first, e.to.second - e.from.second, e.to.third - e.from.third)
			assertEquals(2, d.count { it == 0 }); assertTrue(d.all { it >= 0 })
		}
	}

	@Test
	fun thickBlockAndBrokenMiddle() {
		val cube = (0 until 2).flatMap { x -> (0 until 2).flatMap { y -> (0 until 2).map { z -> p(x, y, z) } } }.toSet()
		val m = BreakableWalls.mesh(cube)
		assertEquals(24, m.faces.size)
		assertEquals(boxEdges(0, 0, 0, 2, 2, 2), m.edges.toSet())

		val split = BreakableWalls.mesh(setOf(p(0, 0, 0), p(2, 0, 0)))
		assertEquals(12, split.faces.size)
		assertEquals(boxEdges(0, 0, 0, 1, 1, 1) + boxEdges(2, 0, 0, 3, 1, 1), split.edges.toSet())
	}

	private val room = listOf(1 to 1)

	@Test
	fun blocksInTheRoomOrOnItsEdge() {
		assertEquals(-168, BreakableWalls.cellStart(1))
		assertTrue(BreakableWalls.touchesRoom(-168, -168, room))
		assertTrue(BreakableWalls.touchesRoom(-150, -150, room))
		assertTrue(BreakableWalls.touchesRoom(-169, -150, room))
		assertTrue(BreakableWalls.touchesRoom(-137, -150, room))
		assertTrue(BreakableWalls.touchesRoom(-150, -137, room))
		assertFalse(BreakableWalls.touchesRoom(-136, -150, room))
		assertFalse(BreakableWalls.touchesRoom(-170, -150, room))
		assertFalse(BreakableWalls.touchesRoom(-150, -120, room))
		assertFalse(BreakableWalls.touchesRoom(-150, -150, emptyList()))
	}

	@Test
	fun multiCellRoomIncludesTheGapBetweenItsCells() {
		val big = listOf(1 to 1, 2 to 1)
		assertTrue(BreakableWalls.touchesRoom(-137, -150, big))
		assertTrue(BreakableWalls.touchesRoom(-120, -150, big))
		assertTrue(BreakableWalls.touchesRoom(-105, -150, big))
		assertFalse(BreakableWalls.touchesRoom(-104, -150, big))

		val l = listOf(1 to 1, 2 to 1, 1 to 2)
		assertTrue(BreakableWalls.touchesRoom(-150, -120, l))
		assertFalse(BreakableWalls.touchesRoom(-120, -120, l))
	}

	@Test
	fun wallsBelongWhenOneBlockTouchesTheRoom() {
		fun wall(x: Int) = (0 until 3).map { p(x, 70 + it, -150) }
		assertTrue(BreakableWalls.inRoom(wall(-150), room))
		assertTrue(BreakableWalls.inRoom(wall(-137) + wall(-136), room))
		assertFalse(BreakableWalls.inRoom(wall(-136) + wall(-135), room))
		assertFalse(BreakableWalls.inRoom(wall(-150), listOf(3 to 3)))
	}
}
