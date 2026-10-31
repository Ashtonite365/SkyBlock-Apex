// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunkCodec
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceStats
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceStore
import java.io.File
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SurfaceStoreTest {
	@TempDir
	lateinit var tmp: File

	private fun terrain() = TestTerrain().apply { for (x in -1..2) for (z in -1..1) load(x to z) }

	@Test
	fun codecRoundTripKeepsEverythingAndTheHash() {
		val t = terrain().apply { fill(3, 65, 3, 3, 70, 3, GRASS); set(9, 65, 9, WATER) }
		val c = t.extract(0, 0, now = 1234)
		val d = SurfaceChunkCodec.decode(SurfaceChunkCodec.encode(c))
		assertEquals(c.x, d.x); assertEquals(c.z, d.z)
		assertArrayEquals(c.top, d.top)
		assertArrayEquals(c.depth, d.depth)
		assertArrayEquals(c.offsets, d.offsets)
		assertEquals(c.edges, d.edges)
		assertEquals(1234L, d.capturedAt)
		for (lx in 0..15) for (lz in 0..15) for (y in 60..72) assertEquals(c.state(lx, y, lz), d.state(lx, y, lz))
		assertEquals(c.contentHash, d.contentHash)
		assertTrue(c.contentEquals(d))
	}

	@Test
	fun corruptDataIsRejected() {
		val bytes = SurfaceChunkCodec.encode(terrain().extract(0, 0))
		bytes[0] = 0
		assertTrue(runCatching { SurfaceChunkCodec.decode(bytes) }.isFailure)
	}

	@Test
	fun putDetectsNewSameAndChanged() {
		val stats = SurfaceStats()
		val s = SurfaceStore(null, stats)
		val t = terrain()
		assertEquals(SurfaceStore.PutResult.NEW, s.put(t.extract(0, 0, now = 1)))
		assertEquals(SurfaceStore.PutResult.SAME, s.put(t.extract(0, 0, now = 2)))
		t.set(4, 65, 4, STONE)
		assertEquals(SurfaceStore.PutResult.CHANGED, s.put(t.extract(0, 0, now = 3)))
		assertEquals(STONE, s.chunk(0, 0)!!.state(4, 65, 4))
		assertEquals(1, stats.cacheHits.get()); assertEquals(1, stats.cacheMisses.get()); assertEquals(1, stats.cacheChanged.get())
	}

	@Test
	fun sameContentWithMoreKnownEdgesIsKept() {
		val s = SurfaceStore(null)
		val t = TestTerrain().apply { load(0 to 0) }
		s.put(t.extract(0, 0))
		assertEquals(0, s.chunk(0, 0)!!.edges)
		t.load(1 to 0)
		s.put(t.extract(0, 0))
		assertNotEquals(0, s.chunk(0, 0)!!.edges)
	}

	@Test
	fun flushWritesRegionFilesAndLoadReadsThemBack() {
		val stats = SurfaceStats()
		val dir = File(tmp, "hypixel/hub")
		val s = SurfaceStore(dir, stats)
		val t = terrain().apply { fill(20, 65, 3, 20, 68, 3, STONE) }
		for (x in 0..1) s.put(t.extract(x, 0))
		s.put(t.extract(-1, 0))
		assertTrue(s.hasDirty())
		assertEquals(2, s.flush())
		assertFalse(s.hasDirty())
		assertTrue(File(dir, SurfaceStore.regionName(0, 0)).isFile)
		assertTrue(File(dir, SurfaceStore.regionName(-1, 0)).isFile)
		assertEquals(2, stats.saves.get())

		val stats2 = SurfaceStats()
		val again = SurfaceStore(dir, stats2)
		assertEquals(3, again.load())
		assertEquals(3, again.chunkCount)
		assertTrue(again.chunk(1, 0)!!.contentEquals(s.chunk(1, 0)!!))
		assertEquals(2, stats2.regionLoads.get())
		assertEquals(3, stats2.islandLoadChunks)
	}

	@Test
	fun islandsNeverMix() {
		val hub = SurfaceStore(File(tmp, "hypixel/hub"))
		val mines = SurfaceStore(File(tmp, "hypixel/dwarven-mines"))
		val hubTerrain = terrain()
		val minesTerrain = TestTerrain { _, y, _ -> if (y <= 120) STONE else "minecraft:air" }.apply { load(0 to 0) }
		hub.put(hubTerrain.extract(0, 0))
		mines.put(minesTerrain.extract(0, 0))
		hub.flush(); mines.flush()
		val hub2 = SurfaceStore(File(tmp, "hypixel/hub")).apply { load() }
		val mines2 = SurfaceStore(File(tmp, "hypixel/dwarven-mines")).apply { load() }
		assertEquals(64, hub2.chunk(0, 0)!!.top[0])
		assertEquals(120, mines2.chunk(0, 0)!!.top[0])
		assertNull(SurfaceStore(File(tmp, "hypixel/garden/x")).apply { load() }.chunk(0, 0))
	}

	@Test
	fun memoryOnlyStoreWritesNothing() {
		val s = SurfaceStore(null)
		s.put(terrain().extract(0, 0))
		assertEquals(0, s.flush())
		assertEquals(0, tmp.listFiles()!!.size)
	}

	@Test
	fun evictKeepsUnsavedData() {
		val s = SurfaceStore(File(tmp, "a"))
		val t = terrain()
		s.put(t.extract(0, 0))
		assertEquals(0, s.evict { false })
		s.flush()
		assertEquals(1, s.evict { false })
		assertEquals(0, s.chunkCount)
	}

	@Test
	fun brokenRegionFileIsSkipped() {
		val dir = File(tmp, "b").apply { mkdirs() }
		File(dir, SurfaceStore.regionName(0, 0)).writeBytes(byteArrayOf(1, 2, 3))
		assertEquals(0, SurfaceStore(dir).load())
	}
}
