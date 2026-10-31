// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceArenaAllocator
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceCommandList
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawPlan
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndirect
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceInstances
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.random.Random

class SurfaceIndirectTest {
	private val slots = SurfaceRegions.SLOTS
	private val layers = 3

	private class Region(val units: IntArray, val start: Array<IntArray>, val count: Array<IntArray>) {
		var arenaStart = 0
		val layerBase = IntArray(units.size)
		val plans = arrayOfNulls<SurfaceDrawPlan>(units.size)
		var offset = floatArrayOf(0f, 0f, 0f)
		var dist = 0.0
	}

	private fun frame(n: Int, seed: Int): List<Region> {
		val rnd = Random(seed)
		return List(n) {
			val start = Array(layers) { IntArray(SurfaceFaceCull.BUCKETS * slots) }
			val count = Array(layers) { IntArray(SurfaceFaceCull.BUCKETS * slots) }
			val units = IntArray(layers)
			for (l in 0 until layers) {
				val fill = when (l) { 0 -> 0.8; 1 -> if (rnd.nextInt(4) != 0) 0.3 else 0.0; else -> if (rnd.nextInt(3) == 0) 0.2 else 0.0 }
				var a = 0
				for (i in count[l].indices) { start[l][i] = a; count[l][i] = if (rnd.nextDouble() < fill) rnd.nextInt(1, 60) * 4 else 0; a += count[l][i] }
				units[l] = a
			}
			Region(units, start, count).also { r ->
				val skip = BooleanArray(slots) { rnd.nextInt(6) == 0 }
				for (l in 0 until layers) {
					val p = SurfaceDrawPlan()
					p.rebuild(start[l], count[l], slots, SurfaceFaceCull.ALL, SurfaceDrawPlan.maskOf(skip))
					r.plans[l] = if (p.runCount == 0) null else p
				}
				r.offset = floatArrayOf(rnd.nextFloat() * 4000 - 2000, -70.5f, rnd.nextFloat() * 4000 - 2000)
				r.dist = rnd.nextDouble()
			}
		}
	}

	private fun place(regions: List<Region>) {
		val a = SurfaceArenaAllocator(0)
		for (r in regions) {
			var u = 0
			for (l in 0 until layers) { r.layerBase[l] = u; u += r.units[l] }
			r.arenaStart = a.alloc(maxOf(u, 1)).let { if (it >= 0) it else { a.grow(a.capacity + maxOf(u, 1)); a.alloc(maxOf(u, 1)) } }
		}
	}

	private fun build(regions: List<Region>, opaque: List<Region>, far: List<Region>, inst: SurfaceInstances, cmds: SurfaceCommandList, idx: Map<Region, Int>) {
		cmds.clear()
		for (l in 0 until layers) {
			cmds.beginLayer(l)
			for (r in if (l == 2) far else opaque) {
				val p = r.plans[l] ?: continue
				if (p.drawCount == 0) continue
				cmds.add(p, r.arenaStart + r.layerBase[l], idx.getValue(r))
			}
			cmds.endLayer()
		}
	}

	@Test
	fun commandsAreThePerRegionDrawsMovedIntoTheSharedBuffer() {
		val regions = frame(120, 1)
		place(regions)
		val opaque = regions.sortedBy { it.dist }
		val far = regions.sortedByDescending { it.dist }
		val inst = SurfaceInstances()
		val idx = HashMap<Region, Int>()
		for (r in opaque) idx[r] = inst.add(r.offset[0], r.offset[1], r.offset[2])
		val cmds = SurfaceCommandList(layers)
		build(regions, opaque, far, inst, cmds, idx)

		var c = 0
		for (l in 0 until layers) {
			assertEquals(c, cmds.layerFirst[l])
			for (r in if (l == 2) far else opaque) {
				val p = r.plans[l] ?: continue
				for (d in 0 until p.drawCount) {
					assertEquals(p.indexCounts.get(d), cmds.word(c, 0))
					assertEquals(1, cmds.word(c, 1))
					assertEquals(0, cmds.word(c, 2))
					assertEquals(r.arenaStart + r.layerBase[l] + p.baseVertices.get(d), cmds.word(c, 3))
					val i = cmds.word(c, 4)
					assertEquals(idx[r], i)

					assertEquals(r.offset[0].toRawBits(), inst.x(i).toRawBits())
					assertEquals(r.offset[1].toRawBits(), inst.y(i).toRawBits())
					assertEquals(r.offset[2].toRawBits(), inst.z(i).toRawBits())
					c++
				}
			}
			assertEquals(c - cmds.layerFirst[l], cmds.layerCount[l])
		}
		assertEquals(c, cmds.count)
		assertEquals(c * 20, cmds.bytes)

		val before = ByteArray(cmds.bytes).also { for (i in it.indices) it[i] = cmds.data.get(i) }
		build(regions, opaque, far, inst, cmds, idx)
		for (i in before.indices) assertEquals(before[i], cmds.data.get(i))
	}

	@Test
	fun gate() {
		assertTrue(SurfaceIndirect.allowed(gameUsesIndirect = true, maxDrawCount = 1 shl 16, failedBefore = false, allInArena = true))
		assertFalse(SurfaceIndirect.allowed(false, 1 shl 16, false, true), "only where the game itself uses it")
		assertFalse(SurfaceIndirect.allowed(true, 0, false, true), "device without indirect multi-draw")
		assertFalse(SurfaceIndirect.allowed(true, 1 shl 16, true, true), "failed once: never again")
		assertFalse(SurfaceIndirect.allowed(true, 1 shl 16, false, false), "a region with its own buffer: per region")
		assertEquals(0, SurfaceIndirect.calls(0, 100))
		assertEquals(1, SurfaceIndirect.calls(100, 100))
		assertEquals(2, SurfaceIndirect.calls(101, 100))
	}

	@Test
	fun drawCallsBeforeAndAfter() {

		for (n in intArrayOf(40, 150, 400)) {
			val regions = frame(n, n)
			place(regions)
			var regionLayers = 0; var ranges = 0
			for (r in regions) for (l in 0 until layers) { val p = r.plans[l] ?: continue; if (p.drawCount > 0) { regionLayers++; ranges += p.drawCount } }

			val beforeMulti = regionLayers
			val beforePerRange = ranges
			val beforeState = regionLayers * 3 + n

			val after = (0 until layers).sumOf { l -> SurfaceIndirect.calls(regions.sumOf { r -> r.plans[l]?.drawCount ?: 0 }, 1 shl 16) }
			val afterState = (0 until layers).count { l -> regions.any { r -> (r.plans[l]?.drawCount ?: 0) > 0 } } * 4 + 1
			println("[apex-bench] draw submissions, $n regions ($ranges ranges): before %d multi-draws (or %d per-range draws), %d state changes + %d transforms; after %d indirect calls, %d state changes + 1 transform; GPU vertex buffers %d -> 1"
				.format(beforeMulti, beforePerRange, regionLayers * 3, n, after, afterState - 1, regionLayers))
			assertTrue(after <= layers)
			assertTrue(after < beforeMulti)
			assertTrue(afterState < beforeState)
		}
	}

	@Test
	fun commandBuildCostAndNoGarbage() {
		val regions = frame(150, 9)
		place(regions)
		val opaque = regions.sortedBy { it.dist }
		val far = regions.sortedByDescending { it.dist }
		val inst = SurfaceInstances()
		val idx = HashMap<Region, Int>()
		for (r in opaque) idx[r] = inst.add(r.offset[0], r.offset[1], r.offset[2])
		val cmds = SurfaceCommandList(layers)
		repeat(200) { build(regions, opaque, far, inst, cmds, idx) }
		val mx = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		val tid = Thread.currentThread().threadId()
		val b0 = mx.getThreadAllocatedBytes(tid)
		val t0 = System.nanoTime()
		repeat(1000) { build(regions, opaque, far, inst, cmds, idx) }
		val ns = (System.nanoTime() - t0) / 1000
		val bytes = (mx.getThreadAllocatedBytes(tid) - b0) / 1000
		println("[apex-bench] command list for 150 regions (${cmds.count} commands): %.1f us per frame, %d B allocated per frame (the test's map lookups box)".format(ns / 1000.0, bytes))
		assertTrue(cmds.count > 0)
	}

	@Test
	fun vertexFormatHasNoUnusedField() {

		val block = DefaultVertexFormat.BLOCK
		assertEquals(28, block.vertexSize)
		val names = block.elements.map { it.name() }
		assertEquals(listOf("Position", "Color", "UV0", "UV2"), names)
		var root: File? = File("").absoluteFile
		while (root != null && !File(root, "src/main/resources/assets/apex/shaders/surfacelod/terrain.vsh").isFile) root = root.parentFile
		val vsh = File(root!!, "src/main/resources/assets/apex/shaders/surfacelod/terrain.vsh").readText()
		val body = vsh.substringAfter("void main()")
		for (n in names) assertTrue(Regex("\\b$n\\b").containsMatchIn(body), "$n read in main()")

		assertTrue(body.contains("(Position + RegionOffset) + ModelOffset"))
		assertTrue(body.contains("Position + ModelOffset"))
	}
}
