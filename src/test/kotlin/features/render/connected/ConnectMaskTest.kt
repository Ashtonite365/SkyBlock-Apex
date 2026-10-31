// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectCore
import dev.ashtonite365.apex.features.render.connected.ConnectMask
import dev.ashtonite365.apex.features.render.connected.ConnectRules
import dev.ashtonite365.apex.features.render.connected.ConnectTiles
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.RIGHT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP
import dev.ashtonite365.apex.features.render.connected.TexAxes
import net.minecraft.core.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class ConnectMaskTest {
	private val glass = "minecraft:glass"
	private val all = ConnectTiles.PLANE_ALL

	private fun wall(s: String = glass) = MapLookup().also { w -> for (x in -1..1) for (y in -1..1) w.set(x, y, 0, s) }

	private fun mask(w: MapLookup, x: Int, y: Int, z: Int, face: Direction, up: Direction, right: Direction, s: String = glass, plane: Int = all): Int {
		val self = st(s)
		return ConnectMask.compute(cacheAt(w, x, y, z), ConnectRules.of(self.block)!!, self, plane, face, up, right, true, null)
	}

	@Test fun `centre of a wall joins all eight neighbours`() {
		assertEquals(0xFF, mask(wall(), 0, 0, 0, Direction.SOUTH, Direction.UP, Direction.EAST))
	}

	@Test fun `corner of a wall joins its three neighbours, in texture space`() {

		assertEquals(RIGHT or DOWN or ConnectTiles.DOWN_RIGHT, mask(wall(), -1, 1, 0, Direction.SOUTH, Direction.UP, Direction.EAST))

		assertEquals(LEFT or DOWN or ConnectTiles.DOWN_LEFT, mask(wall(), -1, 1, 0, Direction.NORTH, Direction.UP, Direction.WEST))
	}

	@Test fun `a neighbour whose face is covered by an opaque block does not join`() {
		val w = wall()
		w.set(1, 0, 1, "minecraft:stone")
		val m = mask(w, 0, 0, 0, Direction.SOUTH, Direction.UP, Direction.EAST)
		assertEquals(0xFF and RIGHT.inv(), m)

		w.set(1, 0, 1, glass)
		assertEquals(0xFF, mask(w, 0, 0, 0, Direction.SOUTH, Direction.UP, Direction.EAST))

		w.set(1, 0, 1, "minecraft:stone")
		assertEquals(0xFF, mask(w, 0, 0, 0, Direction.NORTH, Direction.UP, Direction.WEST))
	}

	@Test fun `different colours in one wall split it`() {
		val w = wall("minecraft:red_stained_glass")
		w.set(1, 0, 0, "minecraft:blue_stained_glass")
		val m = mask(w, 0, 0, 0, Direction.SOUTH, Direction.UP, Direction.EAST, "minecraft:red_stained_glass")
		assertEquals(0, m and RIGHT)
		assertEquals(UP or DOWN or LEFT, m and (UP or DOWN or LEFT or RIGHT))
	}

	@Test fun `row-only textures ignore up and down`() {
		val m = mask(wall("minecraft:bookshelf"), 0, 0, 0, Direction.SOUTH, Direction.UP, Direction.EAST, "minecraft:bookshelf", ConnectTiles.PLANE_ACROSS)
		assertEquals(LEFT or RIGHT, m)
	}

	@Test fun `pillar sides join across the pillar, ends join on the plane`() {
		val w = MapLookup()
		for (x in -1..1) for (y in -1..1) w.set(x, y, 0, "minecraft:purpur_pillar[axis=y]")
		w.set(1, 0, 0, "minecraft:purpur_pillar[axis=x]")
		val r = ConnectRules.of(st("minecraft:purpur_pillar").block)!!
		val side = ConnectMask.compute(cacheAt(w, 0, 0, 0), r, st("minecraft:purpur_pillar[axis=y]"), ConnectTiles.PLANE_ACROSS, Direction.SOUTH, Direction.UP, Direction.EAST, true, null)
		assertEquals(LEFT, side)
	}

	@Test fun `texture axes follow the uv layout, including rotated and mirrored quads`() {
		fun axes(face: Direction, up: Direction, right: Direction): Pair<Direction, Direction> {
			val q = faceQuad(face, up, right)
			val p = TexAxes.derive(face, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11], q[12], q[13], q[14])
			return TexAxes.up(p) to TexAxes.right(p)
		}
		for (face in Direction.entries) for (up in Direction.entries) for (right in Direction.entries) {
			if (up.axis == face.axis || right.axis == face.axis || up.axis == right.axis) continue
			assertEquals(up to right, axes(face, up, right), "$face $up $right")
		}

		val q = faceQuad(Direction.SOUTH, Direction.UP, Direction.EAST, u0 = 0.5f, u1 = 0.5f)
		assertEquals(-1, TexAxes.derive(Direction.SOUTH, q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11], q[12], q[13], q[14]))
	}

	@Test fun `decide picks the table tile for a full face`() {
		val w = wall()
		val self = st(glass)
		val tile = ConnectCore.decide(cacheAt(w, 0, 1, 0), ConnectRules.of(self.block)!!, self, Direction.SOUTH, all, faceQuad(Direction.SOUTH, Direction.UP, Direction.EAST))
		assertEquals(ConnectTiles.tileOf(LEFT or RIGHT or DOWN or ConnectTiles.DOWN_LEFT or ConnectTiles.DOWN_RIGHT), tile)
		assertEquals(ConnectCore.KEEP, ConnectCore.decide(cacheAt(w, 0, 1, 0), ConnectRules.of(self.block)!!, self, Direction.SOUTH, -1, faceQuad(Direction.SOUTH, Direction.UP, Direction.EAST)))
	}

	@Test fun `the neighbour cache reads each cell at most once`() {
		val w = wall()
		val self = st(glass)
		val n = cacheAt(w, 0, 0, 0)
		for (f in Direction.entries) for (up in Direction.entries) for (right in Direction.entries) {
			if (up.axis == f.axis || right.axis == f.axis || up.axis == right.axis) continue
			ConnectMask.compute(n, ConnectRules.of(self.block)!!, self, all, f, up, right, true, null)
		}
		assertEquals(26, n.reads)
	}
}
