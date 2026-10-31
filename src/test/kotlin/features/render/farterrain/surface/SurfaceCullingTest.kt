// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunk
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunkCodec
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.DOWN
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.EAST
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.NORTH
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.SOUTH
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.UP
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.WEST
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceCullingTest {
	private val all = intArrayOf(UP, DOWN, NORTH, SOUTH, WEST, EAST)

	private fun ground(build: TestTerrain.() -> Unit = {}): MapWorld {
		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.build()
		return t.world()
	}

	private fun void(build: TestTerrain.() -> Unit): TestTerrain {
		val t = TestTerrain { _, _, _ -> SurfaceChunk.AIR }
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.build()
		return t
	}

	private fun visible(w: MapWorld, x: Int, y: Int, z: Int, dir: Int, onFace: Boolean = true): Boolean {
		val self = w.chunk(x shr 4, z shr 4)!!.state(x and 15, y, z and 15)!!
		return SurfaceFaces.quadVisible(w, testClassifier, TestShapes, self, x, y, z, dir, onFace)
	}

	private fun mask(w: MapWorld, x: Int, y: Int, z: Int) = SurfaceFaces.mask(w, testClassifier, TestShapes, x, y, z)

	@Test
	fun floorSlabOnGroundHasNoUndersideAndHidesTheGroundTop() {
		val w = ground { set(8, 65, 8, BOTTOM_SLAB) }
		assertFalse(visible(w, 8, 65, 8, DOWN))
		assertFalse(visible(w, 8, 64, 8, UP), "the ground's top under the slab is covered by the slab's full bottom")
		assertTrue(visible(w, 8, 65, 8, UP, onFace = false), "slab open to the sky: its top is drawn")
		assertTrue(visible(w, 8, 65, 8, NORTH))
	}

	@Test
	fun bottomSlabWithAFullBlockAboveHasNoTop() {
		val w = ground { set(8, 65, 8, BOTTOM_SLAB); set(8, 66, 8, STONE) }
		assertFalse(visible(w, 8, 65, 8, UP, onFace = false))
		assertFalse(visible(w, 8, 65, 8, UP))
		assertEquals(0, mask(w, 8, 65, 8) and UP)

		assertTrue(visible(w, 8, 66, 8, DOWN))
	}

	@Test
	fun bottomSlabOpenToTheSkyDrawsItsTop() {
		val w = ground { set(8, 65, 8, BOTTOM_SLAB) }
		assertTrue(visible(w, 8, 65, 8, UP, onFace = false))
		assertTrue(mask(w, 8, 65, 8) and UP != 0)

		val w2 = ground { set(8, 65, 8, BOTTOM_SLAB); set(8, 66, 8, GLASS) }
		assertTrue(visible(w2, 8, 65, 8, UP, onFace = false))
	}

	@Test
	fun overhangCeilingOverAirIsDrawn() {
		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.fill(8, 65, 8, 8, 72, 8, STONE)
		t.fill(9, 70, 8, 9, 72, 8, STONE)
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(9, 8)
		assertEquals(72, c.top[col])
		assertEquals(9, c.depth[col])
		assertEquals(SurfaceChunk.AIR, c.state(9, 69, 8))
		assertEquals(SurfaceChunk.AIR, c.state(9, 66, 8))
		assertEquals(STONE, c.state(9, 64, 8))
		assertEquals(null, c.state(9, 63, 8))
		val w = t.world()
		assertTrue(visible(w, 9, 70, 8, DOWN))
		assertTrue(mask(w, 9, 70, 8) and DOWN != 0)
	}

	@Test
	fun floatingIslandInteriorBottomOverTheVoidIsDrawn() {
		val t = void { fill(0, 50, 0, 15, 60, 15, STONE) }
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(8, 8)
		assertEquals(60, c.top[col])
		assertEquals(12, c.depth[col])
		assertEquals(4, c.offsets[col + 1] - c.offsets[col])
		assertEquals(SurfaceChunk.NO_COLUMN, c.underside(col))
		assertEquals(STONE, c.state(8, 50, 8))
		assertEquals(null, c.state(8, 55, 8))
		assertEquals(SurfaceChunk.AIR, c.state(8, 49, 8))

		val edge = SurfaceIndex.col(0, 8)
		assertEquals(12, c.depth[edge])
		assertEquals(SurfaceChunk.NO_COLUMN, c.underside(edge))
		assertEquals(0, c.undersideCount())
		val w = t.world()
		assertTrue(visible(w, 8, 50, 8, DOWN))
		assertEquals(DOWN, mask(w, 8, 50, 8))
		var bottoms = 0
		SurfaceFaces.forEachVisible(w, testClassifier, TestShapes, c) { _, y, _, _, m -> if (y == 50 && m and DOWN != 0) bottoms++ }
		assertEquals(256, bottoms)
	}

	@Test
	fun cavesAreNeverStored() {

		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.fill(8, 50, 8, 8, 55, 8, SurfaceChunk.AIR)
		val c = t.extract(0, 0)
		assertEquals(1, c.depth[SurfaceIndex.col(8, 8)])
		assertEquals(0, c.undersideCount())

		val f = void { fill(0, 50, 0, 15, 60, 15, STONE); fill(8, 54, 8, 8, 56, 8, SurfaceChunk.AIR) }.extract(0, 0)
		assertEquals(null, f.state(8, 55, 8))
		assertEquals(null, f.state(8, 53, 8))
		assertEquals(STONE, f.state(8, 50, 8))
	}

	@Test
	fun stairsUndersideOverAirIsDrawn() {
		val t = void { set(4, 20, 4, STAIRS_N); set(6, 30, 6, TOP_SLAB) }
		val w = t.world()
		assertTrue(visible(w, 4, 20, 4, DOWN))
		assertTrue(mask(w, 4, 20, 4) and DOWN != 0)
		assertTrue(mask(w, 6, 30, 6) and DOWN != 0, "a top slab's underside over air")
	}

	@Test
	fun aFaceAgainstAFullOpaqueCubeIsNeverDrawn() {
		val w = ground { fill(8, 65, 8, 9, 65, 8, STONE); set(8, 65, 9, STAIRS_N); set(9, 65, 9, BOTTOM_SLAB) }
		assertFalse(visible(w, 8, 65, 8, EAST))
		assertFalse(visible(w, 9, 65, 8, WEST))
		assertFalse(visible(w, 8, 65, 9, NORTH))
		assertFalse(visible(w, 9, 65, 9, NORTH))
		assertFalse(visible(w, 8, 64, 9, UP), "ground under the stair: covered by its full bottom")
		for (d in all) assertFalse(visible(w, 8, 65, 8, d) && d == EAST, "stone against stone")
	}

	@Test
	fun stairBackAgainstAWallHidesBothFaces() {
		val w = ground { set(8, 65, 7, STONE); set(8, 65, 8, STAIRS_N) }
		assertFalse(visible(w, 8, 65, 8, NORTH), "the stair's back")
		assertFalse(visible(w, 8, 65, 7, SOUTH), "the wall's face behind it")
	}

	@Test
	fun stairNotchFacingABlockLeavesThatBlocksFaceDrawn() {
		val w = ground { set(8, 65, 8, STAIRS_N); set(8, 65, 9, STONE) }
		assertTrue(visible(w, 8, 65, 9, NORTH), "part of the stone's face is seen through the notch")
		assertFalse(visible(w, 8, 65, 8, SOUTH), "the stair's half front is fully covered by the stone")

		val w2 = ground { set(8, 65, 8, STAIRS_N); set(8, 65, 7, STAIRS_S) }
		assertFalse(visible(w2, 8, 65, 8, NORTH)); assertFalse(visible(w2, 8, 65, 7, SOUTH))
		val w3 = ground { set(8, 65, 8, STAIRS_S); set(8, 65, 9, STAIRS_N) }
		assertFalse(visible(w3, 8, 65, 8, SOUTH)); assertFalse(visible(w3, 8, 65, 9, NORTH))

		val w4 = ground { set(8, 65, 8, STAIRS_N); set(8, 65, 9, STAIRS_S); set(8, 65, 7, BOTTOM_SLAB) }
		assertFalse(visible(w4, 8, 65, 8, SOUTH)); assertFalse(visible(w4, 8, 65, 9, NORTH))
		assertTrue(visible(w4, 8, 65, 8, NORTH), "full back against a half slab side: the upper half is open")
		assertFalse(visible(w4, 8, 65, 7, SOUTH), "slab side against the stair's full back")
	}

	@Test
	fun stepQuadsInsideTheBlockAreAlwaysDrawn() {
		val w = ground { set(8, 65, 8, STAIRS_N); set(8, 66, 8, STONE) }
		assertTrue(visible(w, 8, 65, 8, UP, onFace = false), "the step's top inside the block shows through the notch")
		assertFalse(visible(w, 8, 65, 8, UP), "the stair's back-half top is covered by the stone")
	}

	@Test
	fun glassNextToGlassHidesTheSharedFace() {
		val w = ground { set(8, 65, 8, GLASS); set(9, 65, 8, GLASS); set(8, 65, 9, STONE) }
		assertFalse(visible(w, 8, 65, 8, EAST)); assertFalse(visible(w, 9, 65, 8, WEST))
		assertFalse(visible(w, 8, 65, 8, SOUTH), "glass against stone")
		assertTrue(visible(w, 8, 65, 9, NORTH), "stone seen through glass")
		assertTrue(visible(w, 8, 65, 8, NORTH))
		val lw = ground { set(8, 65, 8, GLASS); set(9, 65, 8, LEAVES) }
		assertTrue(visible(lw, 8, 65, 8, EAST)); assertTrue(visible(lw, 9, 65, 8, WEST))
	}

	@Test
	fun fluidBottomFollowsTheSameRule() {
		val t = void { set(4, 20, 4, WATER); fill(8, 30, 8, 8, 31, 8, WATER) }
		val w = t.world()
		assertTrue(visible(w, 4, 20, 4, DOWN))
		assertFalse(visible(w, 8, 31, 8, DOWN), "water on water")
		assertTrue(visible(w, 8, 30, 8, DOWN))
		val sea = TestTerrain { _, y, _ -> when { y <= 60 -> SAND; y <= 63 -> WATER; else -> SurfaceChunk.AIR } }
		for (x in -1..1) for (z in -1..1) sea.load(x to z)
		assertFalse(visible(sea.world(), 5, 61, 5, DOWN), "water on the seabed")
	}

	@Test
	fun version1FilesStillDecode() {
		val c = ground { fill(3, 65, 3, 3, 70, 3, GRASS); set(9, 65, 9, WATER) }.chunk(0, 0)!!
		val v1 = SurfaceChunkCodec.encode(c, 1)
		assertEquals(1, v1[4].toInt())
		val d = SurfaceChunkCodec.decode(v1)
		assertEquals(0, d.undersideCount())
		assertArrayEquals(c.top, d.top); assertArrayEquals(c.depth, d.depth); assertArrayEquals(c.blocks.map { c.palette[it] }.toTypedArray(), d.blocks.map { d.palette[it] }.toTypedArray())
		assertEquals(c.contentHash, d.contentHash)
		assertTrue(c.contentEquals(d))

		assertTrue(SurfaceChunkCodec.decode(SurfaceChunkCodec.encode(c)).contentEquals(d))
	}

	@Test
	fun version2RoundTripKeepsUndersidesAndMarkers() {

		val t = void { fill(0, 10, 0, 15, 100, 15, STONE); set(4, 101, 4, GRASS); fill(20, 40, 4, 20, 40, 4, STONE) }
		val c = t.extract(0, 0, now = 77)
		assertTrue(c.blocks.none { it < 0 })
		assertEquals(10, c.underside(SurfaceIndex.col(8, 8)))
		assertTrue(c.undersideCount() > 0)
		val bytes = SurfaceChunkCodec.encode(c)
		assertEquals(SurfaceChunkCodec.VERSION, bytes[4].toInt())
		val d = SurfaceChunkCodec.decode(bytes)
		assertArrayEquals(c.top, d.top); assertArrayEquals(c.depth, d.depth); assertArrayEquals(c.offsets, d.offsets)
		assertEquals(c.undersideCount(), d.undersideCount())
		for (col in 0 until 256) assertEquals(c.underside(col), d.underside(col))
		for (lx in 0..15) for (lz in 0..15) for (y in 5..105) assertEquals(c.state(lx, y, lz), d.state(lx, y, lz), "$lx $y $lz")
		assertTrue(SurfaceChunkCodec.decode(SurfaceChunkCodec.encode(c, 2)).contentEquals(c))
		assertEquals(c.contentHash, d.contentHash)
		assertTrue(c.contentEquals(d))
		assertEquals(77L, d.capturedAt)

		assertFalse(SurfaceChunkCodec.decode(SurfaceChunkCodec.encode(c, 1)).contentEquals(c))
	}
}
