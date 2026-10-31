// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunk
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunkCodec
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChurn
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.DOWN
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.SOUTH
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.UP
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.WEST
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceRecorderTest {
	private fun ground(build: TestTerrain.() -> Unit): TestTerrain {
		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.build()
		return t
	}

	private fun drawn(t: TestTerrain): Map<Triple<Int, Int, Int>, Int> {
		val w = t.world()
		val out = HashMap<Triple<Int, Int, Int>, Int>()
		SurfaceFaces.forEachVisible(w, testClassifier, TestShapes, w.chunk(0, 0)!!) { x, y, z, _, m -> out[Triple(x, y, z)] = m }
		return out
	}

	@Test
	fun bridgeOverGroundStoresTheBridgeAndTheGroundUnderIt() {
		val t = ground { fill(2, 70, 8, 13, 70, 8, STONE) }
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(8, 8)
		assertEquals(70, c.top[col])
		assertEquals(STONE, c.state(8, 70, 8))
		assertEquals(SurfaceChunk.AIR, c.state(8, 67, 8))
		assertEquals(STONE, c.state(8, 64, 8), "the ground under the bridge")
		assertNull(c.state(8, 63, 8))
		val d = drawn(t)
		assertTrue(d.getValue(Triple(8, 64, 8)) and UP != 0, "ground top under the bridge is drawn")
		assertTrue(d.getValue(Triple(8, 70, 8)) and DOWN != 0, "bridge underside is drawn")
	}

	@Test
	fun towerUnderAnOverhangIsStoredWhole() {
		val t = ground {
			fill(5, 65, 5, 5, 75, 5, STONE)
			fill(2, 80, 2, 8, 80, 8, STONE)
		}
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(5, 5)
		assertEquals(80, c.top[col])
		assertEquals(SurfaceChunk.AIR, c.state(5, 78, 5))
		for (y in 65..75) assertEquals(STONE, c.state(5, y, 5), "tower block at $y")
		assertNull(c.state(5, 64, 5), "the ground under the tower stays hidden")
		val d = drawn(t)
		for (y in 65..75) assertTrue(d.getValue(Triple(5, y, 5)) and WEST != 0, "tower side at $y")
		assertTrue(d.getValue(Triple(5, 75, 5)) and UP != 0, "tower top under the roof")
		assertTrue(d.getValue(Triple(4, 64, 4)) and UP != 0, "ground under the roof")
	}

	@Test
	fun roofedAreaOpenOnOneSideKeepsFloorWallsAndContents() {
		val t = ground {
			fill(3, 65, 3, 11, 70, 3, STONE)
			fill(3, 65, 3, 3, 70, 11, STONE)
			fill(11, 65, 3, 11, 70, 11, STONE)
			fill(3, 71, 3, 11, 71, 11, STONE)
			set(7, 65, 7, GLASS)
			set(6, 65, 6, STONE)
		}
		val c = t.extract(0, 0)
		assertEquals(71, c.top[SurfaceIndex.col(7, 7)])
		assertEquals(STONE, c.state(7, 64, 7), "floor")
		assertEquals(GLASS, c.state(7, 65, 7), "see-through contents")
		assertEquals(STONE, c.state(6, 65, 6), "solid contents")
		assertEquals(STONE, c.state(6, 68, 3), "inner face of the north wall")
		val d = drawn(t)
		assertTrue(d.getValue(Triple(6, 68, 3)) and SOUTH != 0, "north wall seen from inside")
		assertTrue(d.getValue(Triple(8, 64, 8)) and UP != 0, "floor")
		assertTrue(d.getValue(Triple(7, 71, 7)) and DOWN != 0, "ceiling")
	}

	@Test
	fun sealedCavesAndRoomsAreNotStored() {
		val t = ground {
			fill(6, 50, 6, 9, 55, 9, SurfaceChunk.AIR)

			fill(2, 65, 2, 12, 70, 2, STONE); fill(2, 65, 12, 12, 70, 12, STONE)
			fill(2, 65, 2, 2, 70, 12, STONE); fill(12, 65, 2, 12, 70, 12, STONE)
			fill(2, 71, 2, 12, 71, 12, STONE)
		}
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(7, 7)
		assertEquals(71, c.top[col])
		assertEquals(1, c.depth[col], "only the roof: the room and the cave under it are sealed")
		assertNull(c.state(7, 64, 7))
		assertNull(c.state(7, 49, 7))
		val cave = SurfaceIndex.col(14, 14)
		assertEquals(1, c.depth[cave])

		val u = TestTerrain().apply {
			load(0 to 0, -1 to 0, 0 to -1, 0 to 1)
			fill(10, 58, 7, 15, 61, 7, SurfaceChunk.AIR)
		}.extract(0, 0)
		assertNull(u.state(12, 57, 7), "tunnel floor")
		assertNull(u.state(12, 59, 7), "tunnel air")
		assertEquals(1, u.depth[SurfaceIndex.col(12, 7)])
	}

	@Test
	fun undersideAndGroundUnderAFloatingMassAreBothStored() {
		val t = ground { fill(4, 80, 4, 10, 84, 10, STONE) }
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(7, 7)
		assertEquals(84, c.top[col])
		assertNull(c.state(7, 82, 7), "inside of the mass")
		assertEquals(STONE, c.state(7, 80, 7), "its bottom over open air")
		assertEquals(STONE, c.state(7, 64, 7), "the ground under it")
		assertTrue(c.blocks.any { it < 0 }, "hidden span inside the column")
		assertTrue(c.offsets[col + 1] - c.offsets[col] < c.depth[col], "spans are run-length encoded")
		assertEquals(c.storedBlocks(), c.blocks.count { it > 0 })
	}

	@Test
	fun version3RoundTripAndOldVersionsStillDecode() {
		val t = ground { fill(2, 70, 8, 13, 70, 8, STONE); fill(4, 80, 4, 10, 84, 10, STONE) }
		val c = t.extract(0, 0, now = 5)
		val bytes = SurfaceChunkCodec.encode(c)
		assertEquals(4, bytes[4].toInt())
		assertTrue(dev.ashtonite365.apex.compress.ApexPack.isApexPack(bytes, 5, 4), "body is an apex pack")
		assertTrue(SurfaceChunkCodec.decode(SurfaceChunkCodec.encode(c, 3)).contentEquals(c), "version 3 (deflate) still decodes")
		val d = SurfaceChunkCodec.decode(bytes)
		assertArrayEquals(c.top, d.top); assertArrayEquals(c.depth, d.depth); assertArrayEquals(c.offsets, d.offsets)
		for (lx in 0..15) for (lz in 0..15) for (y in 60..86) assertEquals(c.state(lx, y, lz), d.state(lx, y, lz), "$lx $y $lz")
		assertTrue(c.contentEquals(d))

		val flat = ground {}.extract(0, 0)
		for (v in 1..2) assertTrue(SurfaceChunkCodec.decode(SurfaceChunkCodec.encode(flat, v)).contentEquals(flat))
	}

	@Test
	fun recaptureThatSeesMoreIsARefinementNotAChange() {
		val t = ground { fill(2, 70, 8, 13, 70, 8, STONE) }
		val full = t.extract(0, 0)

		val col = SurfaceIndex.col(8, 8)
		val blocks = full.blocks.copyOf()
		val o = full.offsets
		val oldBlocks = ArrayList<Int>(); val offsets = IntArray(257); val depth = full.depth.copyOf()
		for (cc in 0 until 256) {
			offsets[cc] = oldBlocks.size
			if (cc == col) { oldBlocks.add(blocks[o[cc]]); depth[cc] = 1 } else for (i in o[cc] until o[cc + 1]) oldBlocks.add(blocks[i])
		}
		offsets[256] = oldBlocks.size
		val old = SurfaceChunk(0, 0, full.palette, full.biomes, full.top, depth, full.columnBiome, oldBlocks.toIntArray(), offsets, 1, full.edges)
		assertNotEquals(old.contentHash, full.contentHash)
		assertEquals(SurfaceChurn.Result.REFINED, SurfaceChurn.compare(old, full).result)
		assertEquals(SurfaceChurn.Result.SAME, SurfaceChurn.compare(full, old).result)

		val changed = ground { fill(2, 70, 8, 13, 70, 8, STONE); set(8, 64, 8, SAND) }.extract(0, 0)
		assertEquals(SurfaceChurn.Result.CHANGED, SurfaceChurn.compare(full, changed).result)
	}
}
