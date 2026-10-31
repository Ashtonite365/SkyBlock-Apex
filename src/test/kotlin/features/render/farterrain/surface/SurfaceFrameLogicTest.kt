// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.LightProps
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBudget
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChunk
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceChurn
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFog
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndexPlan
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndexStorage
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceLight
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceParticlePlan
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceParticlePlan.Status
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceFrameLogicTest {

	@Test
	fun handoffRingFollowsTheGamesRoundedViewDistanceAndLoadedNeighbours() {
		val vd = 12
		val loaded = { x: Int, z: Int -> Math.abs(x) <= 13 && Math.abs(z) <= 13 }

		assertTrue(SurfaceHandoff.fallbackOwned(0, 0, vd, 12, 0, loaded))
		assertFalse(SurfaceHandoff.fallbackOwned(0, 0, vd, 13, 0, loaded), "outer loaded ring is never meshed by the game")

		assertFalse(SurfaceHandoff.fallbackOwned(0, 0, vd, 12, 12, loaded))
		assertTrue(SurfaceHandoff.fallbackOwned(0, 0, vd, 8, 8, loaded))

		val holed = { x: Int, z: Int -> loaded(x, z) && !(x == 5 && z == 5) }
		assertFalse(SurfaceHandoff.fallbackOwned(0, 0, vd, 4, 4, holed))
		assertFalse(SurfaceHandoff.fallbackOwned(0, 0, vd, 5, 5, holed))

		for (r in 1..vd) assertTrue(SurfaceHandoff.fallbackOwned(0, 0, vd, r, 0, loaded), "ring $r")
	}

	@Test
	fun cameraChunkUsesFloor() {
		assertEquals(-1, SurfaceHandoff.chunkOf(-0.5))
		assertEquals(-1, SurfaceHandoff.chunkOf(-16.0))
		assertEquals(-2, SurfaceHandoff.chunkOf(-16.5))
		assertEquals(0, SurfaceHandoff.chunkOf(15.9))
	}

	private class FakeIndices(var capacity: Int) : SurfaceIndexStorage {
		override fun hasStorage(indexCount: Int) = indexCount <= capacity
		override fun ensure(indexCount: Int) { capacity = maxOf(capacity, indexCount) }
	}

	@Test
	fun longRunsWereSkippedUntilTheIndexBufferIsGrown() {
		val runs = listOf(SurfaceDrawRuns.Run(0, 400), SurfaceDrawRuns.Run(400, 40_000), SurfaceDrawRuns.Run(40_400, 4_000))

		val small = FakeIndices(6_000)
		assertEquals(2, SurfaceIndexPlan.drawable(small, runs).size, "old flow: the long ground run is skipped")
		val bigger = FakeIndices(60_000)
		assertEquals(3, SurfaceIndexPlan.drawable(bigger, runs).size, "a different camera section: it comes back")
		SurfaceIndexPlan.prepare(small, SurfaceIndexPlan.maxIndices(runs))
		assertEquals(runs, SurfaceIndexPlan.drawable(small, runs), "after prepare every run draws")
		assertEquals(60_000, SurfaceIndexPlan.maxIndices(runs))
	}

	@Test
	fun budgetKeepsTheNearest() {
		val e = listOf(SurfaceBudget.Entry(1, 900, 10), SurfaceBudget.Entry(2, 1, 10), SurfaceBudget.Entry(3, 50, 10), SurfaceBudget.Entry(4, 400, 10))
		assertEquals(listOf(4L, 1L), SurfaceBudget.toDrop(e, 2, 1000))
		assertEquals(listOf(1L), SurfaceBudget.toDrop(e, 10, 30))
		assertEquals(listOf(3L, 4L, 1L), SurfaceBudget.toDrop(e, 0, 0), "the nearest always stays")
	}

	@Test
	fun fogIsPushedOutNeverPulledIn() {
		assertEquals(500f, SurfaceFog.pushOut(192f, 500f))
		assertEquals(192f, SurfaceFog.pushOut(192f, 100f))
		assertEquals(1f, SurfaceFog.envScaleFor(false, 4096f))
		assertEquals(1f, SurfaceFog.envScaleFor(true, 512f))
		assertEquals(4f, SurfaceFog.envScaleFor(true, 4096f))
	}

	@Test
	fun particlePlanScalesWithDistanceAndRespectsSettings() {
		assertEquals(SurfaceParticlePlan.BASE_CHANCE, SurfaceParticlePlan.chance(10.0, 64))
		assertEquals(SurfaceParticlePlan.BASE_CHANCE / 2, SurfaceParticlePlan.chance(64.0, 64), 1e-12)
		assertEquals(0.0, SurfaceParticlePlan.chance(65.0, 64))
		assertEquals(0.0, SurfaceParticlePlan.chance(5.0, 0), "particle distance 0 = off")
		assertTrue(SurfaceParticlePlan.vanillaAnimates(0, 64, 0, 31, 64, -31, loaded = true))
		assertFalse(SurfaceParticlePlan.vanillaAnimates(0, 64, 0, 32, 64, 0, loaded = true))
		assertFalse(SurfaceParticlePlan.vanillaAnimates(0, 64, 0, 3, 64, 0, loaded = false))
		assertTrue(SurfaceParticlePlan.keep(Status.ALL, false) { 0 })
		assertFalse(SurfaceParticlePlan.keep(Status.MINIMAL, false) { 0 })
		assertTrue(SurfaceParticlePlan.keep(Status.MINIMAL, true) { n -> if (n == 10) 0 else 1 }, "always-show promotion")
		assertFalse(SurfaceParticlePlan.keep(Status.DECREASED, false) { 0 })
		assertTrue(SurfaceParticlePlan.keep(Status.DECREASED, false) { 1 })
		assertEquals(SurfaceParticlePlan.MAX_PER_TICK.toDouble(), SurfaceParticlePlan.expectedPerTick(1_000_000, 10.0, 64))
	}

	private fun flat(build: TestTerrain.() -> Unit = {}): SurfaceChunk {
		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.build()
		return t.extract(0, 0)
	}

	@Test
	fun tickingStatesAreNotChanges() {
		assertEquals("minecraft:wheat", SurfaceChurn.stable("minecraft:wheat[age=3]"))
		assertEquals("minecraft:water", SurfaceChurn.stable("minecraft:water[level=4]"))
		assertEquals("minecraft:oak_leaves[waterlogged=false]", SurfaceChurn.stable(LEAVES))
		val a = flat { set(4, 65, 4, "minecraft:wheat[age=1]") }
		val b = flat { set(4, 65, 4, "minecraft:wheat[age=7]") }
		assertEquals(SurfaceChurn.Result.SAME, SurfaceChurn.compare(a, b).result)
		val c = flat { set(4, 65, 4, "minecraft:carrots[age=7]") }
		val o = SurfaceChurn.compare(a, c)
		assertEquals(SurfaceChurn.Result.CHANGED, o.result)
		assertTrue(o.firstDifference!!.contains("65"), o.firstDifference)
	}

	@Test
	fun missingNeighbourCaptureIsRefinedLater() {
		val t = TestTerrain { _, y, _ -> if (y <= 70) STONE else SurfaceChunk.AIR }
		t.fill(16, 65, 0, 31, 70, 15, SurfaceChunk.AIR)
		t.load(0 to 0)
		val shallow = t.extract(0, 0)
		t.load(1 to 0, -1 to 0, 0 to 1, 0 to -1)
		val deep = t.extract(0, 0)
		assertEquals(SurfaceChurn.Result.REFINED, SurfaceChurn.compare(shallow, deep).result)
		assertEquals(SurfaceChurn.Result.SAME, SurfaceChurn.compare(deep, shallow).result, "a shallower re-capture keeps the deeper data")
		assertTrue(SurfaceChurn.edgesNowAvailable(shallow.edges) { dx, _ -> dx == 1 })
		assertFalse(SurfaceChurn.edgesNowAvailable(SurfaceChunk.ALL_EDGES) { _, _ -> true })
		assertFalse(SurfaceChurn.edgesNowAvailable(0) { _, _ -> false })
	}

	private object Props : LightProps {
		override fun emission(state: String) = if (state == TORCH) 14 else 0
		override fun dampening(state: String) = when (state) {
			SurfaceChunk.AIR, GLASS, TORCH -> 0
			LEAVES, WATER -> 1
			else -> 15
		}
	}

	@Test
	fun lightFromStoredData() {
		val t = TestTerrain()
		for (x in -1..1) for (z in -1..1) t.load(x to z)
		t.fill(2, 70, 2, 8, 70, 8, STONE)
		t.fill(10, 67, 10, 10, 68, 10, LEAVES)
		t.set(12, 65, 3, TORCH)
		t.fill(13, 65, 0, 13, 69, 6, STONE)
		val light = SurfaceLight(t.world(), Props, 0, 0, 15, 15)
		assertEquals(15, light.sky(0, 65, 0), "open sky")
		assertTrue(light.canSeeSky(0, 65, 0))
		assertFalse(light.canSeeSky(5, 65, 5))
		val under = light.sky(5, 65, 5)
		assertTrue(under in 1..14, "under the roof: spread from the sides ($under)")
		assertTrue(light.directSky(10, 65, 10) < 15, "leaves dampen")
		assertEquals(14, light.block(12, 65, 3))
		assertEquals(13, light.block(11, 65, 3))
		assertTrue(light.block(14, 65, 3) <= 4, "the flood stops at the opaque wall (only light around it arrives)")
	}

	companion object {
		const val TORCH = "minecraft:torch"
	}
}
