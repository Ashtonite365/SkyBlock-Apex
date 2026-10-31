// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.BreakableWalls
import dev.ashtonite365.apex.features.dungeon.BreakableWalls.WallStyle

class BreakableWallsStyleTest {
	private val wall = setOf(Triple(0, 70, 0), Triple(1, 70, 0), Triple(2, 70, 0))

	@Test
	fun singleBlockIsOneBoxPerBlock() {
		val m = BreakableWalls.shape(wall, WallStyle.SINGLE_BLOCK)
		assertEquals(18, m.faces.size)
		assertEquals(36, m.edges.size)
	}

	@Test
	fun meshIsMerged() {
		val m = BreakableWalls.shape(wall, WallStyle.MESH)
		assertEquals(BreakableWalls.mesh(wall), m)
		assertEquals(14, m.faces.size)
		assertEquals(12, m.edges.size)
	}
}
