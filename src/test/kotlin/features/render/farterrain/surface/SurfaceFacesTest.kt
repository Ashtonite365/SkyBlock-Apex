// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunk
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.DOWN
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.EAST
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.NORTH
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.SOUTH
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.UP
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaces.WEST
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceFacesTest {
	private fun terrain(build: TestTerrain.() -> Unit = {}): MapWorld {
		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.build()
		return t.world()
	}

	private fun mask(w: MapWorld, x: Int, y: Int, z: Int) = SurfaceFaces.mask(w, testClassifier, x, y, z)

	@Test
	fun flatGroundEmitsOnlyTopFaces() {
		val w = terrain()
		assertEquals(UP, mask(w, 5, 64, 5))
		assertEquals(UP, mask(w, 0, 64, 0))
		var faces = 0
		val c = w.chunk(0, 0)!!
		SurfaceFaces.forEachVisible(w, testClassifier, c) { _, _, _, _, m -> faces += SurfaceFaces.faceCount(m) }
		assertEquals(256, faces)
	}

	@Test
	fun cliffEmitsSideFacesDownToTheLowerNeighbour() {
		val w = terrain { fill(8, 65, 8, 8, 67, 8, STONE) }
		assertEquals(UP or NORTH or SOUTH or WEST or EAST, mask(w, 8, 67, 8))
		assertEquals(NORTH or SOUTH or WEST or EAST, mask(w, 8, 65, 8))
		assertEquals(0, mask(w, 8, 64, 8))
		assertEquals(UP, mask(w, 7, 64, 8))
	}

	@Test
	fun bottomFaceOverAirIsEmitted() {
		val t = TestTerrain { _, _, _ -> SurfaceChunk.AIR }
		t.load(0 to 0)
		t.set(4, 20, 4, STONE)
		val w = t.world()
		assertEquals(UP or DOWN or NORTH or SOUTH or WEST or EAST, mask(w, 4, 20, 4))
	}

	@Test
	fun transparentBlocksAreDrawnButDoNotCull() {
		val w = terrain { set(8, 65, 8, GLASS) }
		assertEquals(UP or NORTH or SOUTH or WEST or EAST, mask(w, 8, 65, 8))
		val w2 = terrain { fill(8, 65, 8, 9, 65, 8, STONE); set(8, 66, 8, LEAVES) }
		assertTrue(mask(w2, 8, 65, 8) and UP != 0, "leaves on top do not hide the stone's top face")
	}

	@Test
	fun sameTransparentStateCullsItself() {
		val w = TestTerrain { _, y, _ -> when { y <= 60 -> SAND; y <= 63 -> WATER; else -> SurfaceChunk.AIR } }.apply {
			for (x in -1..1) for (z in -1..1) load(x to z)
		}.world()
		assertEquals(UP, mask(w, 5, 63, 5))
		assertEquals(0, mask(w, 5, 62, 5))
		assertEquals(UP, mask(w, 5, 60, 5))
	}

	@Test
	fun unrecordedNeighbourChunkDrawsTheBorderFace() {
		val t = TestTerrain()
		t.load(0 to 0)
		t.fill(15, 65, 3, 15, 66, 3, STONE)
		val w = MapWorld().also { it.put(t.extract(0, 0)) }
		val m = mask(w, 15, 66, 3)

		assertEquals(UP or NORTH or SOUTH or WEST or EAST, m)

		var skirt = 0
		SurfaceFaces.forEachVisible(w, testClassifier, TestShapes, w.chunk(0, 0)!!) { x, y, z, _, mm -> if (x == 15 && z != 3 && y < 64 && mm and EAST != 0) skirt++ }
		assertEquals(15 * SurfaceFaces.UNKNOWN_SKIRT, skirt)
	}

	@Test
	fun airIsNeverEmitted() {
		val w = terrain()
		assertEquals(0, mask(w, 5, 65, 5))
		assertEquals(0, mask(w, 5, 100, 5))
	}

	private fun voidWorld(neighboursRecorded: Boolean, build: TestTerrain.() -> Unit): MapWorld {
		val t = TestTerrain { _, _, _ -> SurfaceChunk.AIR }
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.build()
		if (neighboursRecorded) return t.world()
		return MapWorld().also { it.put(t.extract(0, 0)) }
	}

	private fun drawn(w: MapWorld): Map<Pair<Int, Int>, Int> {
		val low = HashMap<Pair<Int, Int>, Int>()
		for (cx in -1..1) for (cz in -1..1) {
			val c = w.chunk(cx, cz) ?: continue
			SurfaceFaces.forEachVisible(w, testClassifier, TestShapes, c) { x, y, z, _, m ->
				if (m != 0) low.merge(x to z, y) { a, b -> minOf(a, b) }
			}
		}
		return low
	}

	@Test
	fun floatingIslandOverTheVoidDrawsNothingBelowItsBottomFace() {
		for (recorded in listOf(true, false)) {
			val w = voidWorld(recorded) { fill(2, 60, 2, 10, 64, 10, STONE) }
			val low = drawn(w)
			assertTrue(low.isNotEmpty())
			for ((xz, y) in low) {
				assertTrue(xz.first in 2..10 && xz.second in 2..10, "drawn outside the island at $xz (recorded=$recorded)")
				assertTrue(y >= 60, "drawn at y=$y below the island bottom 60 at $xz (recorded=$recorded)")
			}

			assertTrue(mask(w, 5, 60, 5) and DOWN != 0)
			assertTrue(mask(w, 2, 60, 5) and DOWN != 0)
		}
	}

	@Test
	fun spireOverAirDrawsNothingBelowItsBottomFace() {
		for (recorded in listOf(true, false)) {

			val w = voidWorld(recorded) { fill(15, 40, 7, 15, 120, 7, STONE) }
			val low = drawn(w)
			assertEquals(setOf(15 to 7), low.keys, "recorded=$recorded")
			assertEquals(40, low[15 to 7], "recorded=$recorded")
			assertTrue(mask(w, 15, 40, 7) and DOWN != 0)
		}
	}

	@Test
	fun overhangKeepsBothRunsButNothingUnderTheBridge() {

		val w = terrain { fill(0, 80, 0, 15, 81, 3, STONE) }
		assertTrue(mask(w, 5, 80, 1) and DOWN != 0)
		val c = w.chunk(0, 0)!!
		SurfaceFaces.forEachVisible(w, testClassifier, TestShapes, c) { x, y, z, _, m ->
			if (m != 0 && z in 0..3) assertTrue(y == 64 || y >= 80, "drawn in the air under the bridge at $x,$y,$z")
		}
	}
}
