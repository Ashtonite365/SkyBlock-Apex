// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunk
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceExtractorTest {
	private fun loadedAround(t: TestTerrain) = t.load(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)

	@Test
	fun flatGroundStoresOnlyTheTopBlock() {
		val t = TestTerrain()
		loadedAround(t)
		val c = t.extract(0, 0)
		for (col in 0 until 256) {
			assertEquals(64, c.top[col])
			assertEquals(1, c.depth[col], "column $col")
		}
		assertEquals(STONE, c.state(5, 64, 5))
		assertEquals(SurfaceChunk.AIR, c.state(5, 65, 5))
		assertEquals(null, c.state(5, 63, 5))
		assertEquals(256, c.storedBlocks())
		assertEquals(SurfaceChunk.ALL_EDGES, c.edges)
	}

	@Test
	fun cliffColumnStoresItsExposedSide() {
		val t = TestTerrain()
		loadedAround(t)
		t.fill(8, 65, 8, 8, 70, 8, STONE)
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(8, 8)
		assertEquals(70, c.top[col])
		assertEquals(6, c.depth[col])
		assertEquals(1, c.depth[SurfaceIndex.col(7, 8)])
	}

	@Test
	fun columnNextToAPitStoresTheWallDownToThePitFloor() {
		val t = TestTerrain()
		loadedAround(t)
		t.fill(4, 60, 4, 4, 64, 4, SurfaceChunk.AIR)
		val c = t.extract(0, 0)
		val hole = SurfaceIndex.col(4, 4)
		assertEquals(59, c.top[hole])
		assertEquals(1, c.depth[hole])
		val wall = SurfaceIndex.col(5, 4)
		assertEquals(64, c.top[wall])
		assertEquals(5, c.depth[wall])
	}

	@Test
	fun waterOverSandKeepsWaterAndTheSeabed() {
		val t = TestTerrain { _, y, _ -> when { y <= 60 -> SAND; y <= 63 -> WATER; else -> SurfaceChunk.AIR } }
		loadedAround(t)
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(3, 3)
		assertEquals(63, c.top[col])
		assertEquals(4, c.depth[col])
		assertEquals(WATER, c.state(3, 63, 3))
		assertEquals(WATER, c.state(3, 61, 3))
		assertEquals(SAND, c.state(3, 60, 3))
		assertEquals(null, c.state(3, 59, 3))
	}

	@Test
	fun unknownNeighbourIsAssumedLevelAndRecordedInEdges() {
		val t = TestTerrain()
		t.load(0 to 0, 1 to 0)
		t.fill(0, 65, 0, 15, 70, 15, STONE)
		val c = t.extract(0, 0)
		assertEquals(SurfaceChunk.EDGE_EAST, c.edges)
		assertEquals(6, c.depth[SurfaceIndex.col(15, 5)])
		assertEquals(1, c.depth[SurfaceIndex.col(0, 5)])
		assertEquals(1, c.depth[SurfaceIndex.col(5, 0)])
	}

	@Test
	fun anAirGapOpenToTheSideDoesNotStopTheColumn() {
		val t = TestTerrain()
		loadedAround(t)

		t.fill(8, 65, 8, 8, 80, 8, STONE)
		t.set(8, 70, 8, SurfaceChunk.AIR)
		val c = t.extract(0, 0)
		val col = SurfaceIndex.col(8, 8)
		assertEquals(80, c.top[col])
		assertEquals(16, c.depth[col])
		assertEquals(SurfaceChunk.AIR, c.state(8, 70, 8))
		assertEquals(STONE, c.state(8, 69, 8))
		assertEquals(STONE, c.state(8, 65, 8))
		assertEquals(null, c.state(8, 64, 8))
	}

	@Test
	fun emptyColumnsAndBiomes() {
		val t = TestTerrain { _, _, _ -> SurfaceChunk.AIR }
		t.biome = { wx, _ -> if (wx < 8) "minecraft:desert" else "minecraft:forest" }
		loadedAround(t)
		t.set(2, 10, 2, GRASS)
		t.set(12, 10, 2, GRASS)
		val c = t.extract(0, 0)
		assertTrue(c.isEmpty(SurfaceIndex.col(0, 0)))
		assertEquals(SurfaceChunk.NO_COLUMN, c.top[SurfaceIndex.col(0, 0)])
		assertEquals("minecraft:desert", c.biomes[c.columnBiome[SurfaceIndex.col(2, 2)]])
		assertEquals("minecraft:forest", c.biomes[c.columnBiome[SurfaceIndex.col(12, 2)]])

		assertEquals(2, c.depth[SurfaceIndex.col(2, 2)])
		assertEquals(2, c.storedBlocks())
	}

	@Test
	fun allAirChunkIsAllAir() {
		val t = TestTerrain { _, _, _ -> SurfaceChunk.AIR }
		loadedAround(t)
		assertTrue(t.extract(0, 0).isAllAir)
	}
}
