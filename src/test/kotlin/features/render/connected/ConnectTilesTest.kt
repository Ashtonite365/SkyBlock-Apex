// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectTiles
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN_LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN_RIGHT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.RIGHT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP_LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP_RIGHT
import dev.ashtonite365.apex.features.render.connected.TileArt
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ConnectTilesTest {
	private val edges = UP or RIGHT or DOWN or LEFT
	private val corners = UP_RIGHT or DOWN_RIGHT or DOWN_LEFT or UP_LEFT

	@Test fun `there are 47 distinct tiles and no connection is the original texture`() {
		assertEquals(47, ConnectTiles.TILE_COUNT)
		assertEquals(0, ConnectTiles.tileOf(0))
		assertEquals(0, ConnectTiles.keyOfTile(0))
		assertEquals(47, (0 until 256).map { ConnectTiles.tileOf(it) }.toSet().size)
	}

	@Test fun `a diagonal only matters when both its edges connect`() {
		for (m in 0 until 256) {
			for ((diag, a, b) in listOf(Triple(UP_RIGHT, UP, RIGHT), Triple(DOWN_RIGHT, DOWN, RIGHT), Triple(DOWN_LEFT, DOWN, LEFT), Triple(UP_LEFT, UP, LEFT))) {
				val flipped = m xor diag
				val both = m and a != 0 && m and b != 0
				if (both) assertNotEquals(ConnectTiles.tileOf(m), ConnectTiles.tileOf(flipped), "mask $m diag $diag")
				else assertEquals(ConnectTiles.tileOf(m), ConnectTiles.tileOf(flipped), "mask $m diag $diag")
			}
		}
		assertEquals(ConnectTiles.tileOf(UP_RIGHT or DOWN_LEFT), 0)
	}

	@Test fun `interior and inner corners are separate tiles`() {
		val full = ConnectTiles.tileOf(edges or corners)
		val crossNoCorners = ConnectTiles.tileOf(edges)
		assertNotEquals(full, crossNoCorners)
		assertEquals(0, ConnectTiles.keyOfTile(full) and 0xF0)
		assertEquals(0xF0, ConnectTiles.keyOfTile(crossNoCorners) and 0xF0)
	}

	@Test fun `plane restriction limits the reachable tiles`() {
		assertEquals(47, ConnectTiles.reachable(ConnectTiles.PLANE_ALL).size)
		val across = ConnectTiles.reachable(ConnectTiles.PLANE_ACROSS)
		assertEquals(setOf(0, ConnectTiles.tileOf(LEFT), ConnectTiles.tileOf(RIGHT), ConnectTiles.tileOf(LEFT or RIGHT)), across.toSet())
		assertEquals(4, ConnectTiles.reachable(ConnectTiles.PLANE_ALONG).size)
	}

	private fun src(size: Int) = IntArray(size * size) { (it * 2654435761L).toInt() or 0xFF000000.toInt() }

	@Test fun `tile art for no connection is the source unchanged`() {
		val s = src(16)
		assertArrayEquals(s, TileArt.generate(s, 16, 0))
	}

	@Test fun `joined edges lose their frame by mirroring the inside`() {
		val s = src(16)
		val t = TileArt.generate(s, 16, ConnectTiles.keyOf(LEFT or RIGHT))
		for (y in 1 until 15) {
			assertEquals(s[y * 16 + 1], t[y * 16 + 0], "left column at $y")
			assertEquals(s[y * 16 + 14], t[y * 16 + 15], "right column at $y")
		}
		for (x in 0 until 16) {
			assertEquals(s[x], t[x], "top row kept at $x")
			assertEquals(s[15 * 16 + x], t[15 * 16 + x], "bottom row kept at $x")
		}
		for (y in 1 until 15) for (x in 1 until 15) assertEquals(s[y * 16 + x], t[y * 16 + x])
	}

	@Test fun `inner corner keeps the corner pixel of the frame`() {
		val s = src(16)
		val t = TileArt.generate(s, 16, ConnectTiles.keyOf(UP or LEFT))
		assertEquals(s[0], t[0])
		assertEquals(s[16 + 5], t[16 + 5])
		assertEquals(s[1 * 16 + 5], t[0 * 16 + 5])
		val full = TileArt.generate(s, 16, ConnectTiles.keyOf(UP or LEFT or UP_LEFT))
		assertEquals(s[1 * 16 + 1], full[0])
	}

	@Test fun `tile art scales the frame with the texture size`() {
		assertEquals(1, TileArt.border(16))
		assertEquals(2, TileArt.border(32))
		assertEquals(1, TileArt.border(8))
		val s = src(32)
		val t = TileArt.generate(s, 32, ConnectTiles.keyOf(LEFT))
		assertEquals(s[5 * 32 + 3], t[5 * 32 + 0])
		assertEquals(s[5 * 32 + 2], t[5 * 32 + 1])
	}
}
