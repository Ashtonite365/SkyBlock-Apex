// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceErrorLog
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFog
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffGeometry
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfacePassTextures
import java.util.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceHandoffGapTest {
	private val rds = intArrayOf(8, 12, 16, 32)

	private fun vanillaDrawsSection(cx: Int, sy: Int, cz: Int, camX: Double, camY: Double, camZ: Double, rd: Int): Boolean =
		SurfaceHandoff.inDisc(SurfaceHandoffGeometry.chunkOf(camX), SurfaceHandoffGeometry.chunkOf(camZ), rd, cx, cz) &&
			Math.abs(sy - (Math.floor(camY).toInt() shr 4)) <= rd

	private fun cylinderDrawsSection(cx: Int, sy: Int, cz: Int, camX: Double, camY: Double, camZ: Double, limit: Double): Boolean {
		fun axis(c: Double, o: Int): Double { val lo = o - 1.0; val hi = o + 17.0; return if (c < lo) lo - c else if (c > hi) c - hi else 0.0 }
		val dx = axis(camX, cx * 16); val dz = axis(camZ, cz * 16); val dy = axis(camY, sy * 16)
		return dx * dx + dz * dz < limit * limit && dy < limit
	}

	private fun farHides(cx: Int, cz: Int, yLo: Int, yHi: Int, x: Double, y: Double, z: Double, rd: Int, limit: Double): Boolean {
		val owns = SurfaceHandoffGeometry.verticalOwns(yLo, yHi, y, rd, 0, limit) && SurfaceHandoffGeometry.gameOwns(cx, cz, x, z, rd, limit)
		if (!owns) return false
		val preDraw = SurfaceHandoffGeometry.verticalEdge(yLo, yHi, y, rd, limit) || SurfaceHandoffGeometry.outerRings(cx, cz, x, z, rd, limit)
		return !preDraw
	}

	private fun farOwns(cx: Int, cz: Int, yLo: Int, yHi: Int, x: Double, y: Double, z: Double, rd: Int, limit: Double) =
		SurfaceHandoffGeometry.verticalOwns(yLo, yHi, y, rd, 0, limit) && SurfaceHandoffGeometry.gameOwns(cx, cz, x, z, rd, limit)

	private fun terrain(cx: Int, cz: Int): IntArray {
		val r = Random(cx * 341873128712L + cz * 132897987541L)
		val lo = 30 + r.nextInt(60)
		val hi = lo + 1 + when (r.nextInt(4)) { 0 -> r.nextInt(4); 1 -> r.nextInt(30); 2 -> r.nextInt(120); else -> r.nextInt(10) }
		return intArrayOf(lo, hi)
	}

	private class Gaps { var vanilla = 0; var cylinder = 0; var checked = 0; var hidden = 0 }

	private fun scan(g: Gaps, x: Double, y: Double, z: Double, rd: Int, fogEnd: Double, ownsOnly: Boolean,
		decideX: Double = x, decideY: Double = y, decideZ: Double = z, oldRule: Boolean = false) {
		val limit = SurfaceHandoffGeometry.limit(rd, fogEnd)
		val ccx = SurfaceHandoffGeometry.chunkOf(x); val ccz = SurfaceHandoffGeometry.chunkOf(z)
		for (cx in ccx - rd - 3..ccx + rd + 3) for (cz in ccz - rd - 3..ccz + rd + 3) {
			val t = terrain(cx, cz)
			val hides = when {
				oldRule -> SurfaceHandoffGeometry.gameOwns(cx, cz, decideX, decideZ, rd)
				ownsOnly -> farOwns(cx, cz, t[0], t[1], decideX, decideY, decideZ, rd, limit)
				else -> farHides(cx, cz, t[0], t[1], decideX, decideY, decideZ, rd, limit)
			}
			g.checked++
			if (!hides) continue
			g.hidden++
			var vGap = false; var cGap = false
			for (sy in (t[0] shr 4)..((t[1] - 1) shr 4)) {
				if (!vanillaDrawsSection(cx, sy, cz, x, y, z, rd)) vGap = true
				if (!cylinderDrawsSection(cx, sy, cz, x, y, z, limit)) cGap = true
			}
			if (vGap) g.vanilla++
			if (cGap) g.cylinder++
		}
	}

	private val heights = doubleArrayOf(64.5, 95.0, 128.0, 160.25, 200.0, 255.9, 300.0, 400.0, 31.0, -20.0)
	private val offsets = doubleArrayOf(0.01, 0.5, 7.5, 8.0, 15.99)

	@Test
	fun noGapAtAnyHeightRenderDistanceOrSubChunkPosition() {
		for (rd in rds) {
			val g = Gaps()
			for (y in heights) for (fx in offsets) for (fz in offsets) {
				if (rd == 32 && (fx != fz)) continue
				scan(g, 1000.0 + fx, y, -500.0 + fz, rd, Double.POSITIVE_INFINITY, ownsOnly = true)
			}
			println("rd $rd: ${g.checked} columns checked, ${g.hidden} owned by the game, gaps vanilla ${g.vanilla} cylinder ${g.cylinder}")
			assertTrue(g.hidden > 0)
			assertEquals(0, g.vanilla, "vanilla gaps at rd $rd")
			assertEquals(0, g.cylinder, "cylinder gaps at rd $rd")
		}
	}

	@Test
	fun theOldRuleLeftTheRingHighAboveTheGround() {

		for (rd in rds) {
			val g = Gaps()
			for (y in doubleArrayOf(16.0 * rd + 120, 16.0 * rd + 200)) scan(g, 1000.5, y, -500.5, rd, Double.POSITIVE_INFINITY, ownsOnly = true, oldRule = true)
			println("rd $rd old rule high up: vanilla gaps ${g.vanilla}, cylinder gaps ${g.cylinder}")
			assertTrue(g.vanilla > 0 && g.cylinder > 0, "the old rule must show the ring at rd $rd")
			val n = Gaps()
			for (y in doubleArrayOf(16.0 * rd + 120, 16.0 * rd + 200)) scan(n, 1000.5, y, -500.5, rd, Double.POSITIVE_INFINITY, ownsOnly = true)
			assertEquals(0, n.vanilla); assertEquals(0, n.cylinder)
		}
	}

	@Test
	fun noGapInFogThatEndsBeforeTheRenderDistance() {
		for (rd in rds) for (fog in doubleArrayOf(24.0, 48.0, 96.0, 200.0)) {
			val g = Gaps()
			for (y in doubleArrayOf(64.5, 140.0)) for (f in offsets) scan(g, 1000.0 + f, y, -500.0 + f, rd, fog, ownsOnly = true)
			assertEquals(0, g.cylinder, "fog $fog rd $rd")
			assertEquals(0, g.vanilla, "fog $fog rd $rd")
		}
	}

	@Test
	fun noGapWhileMovingFastIncludingDiagonalsAndClimbing() {

		val steps = arrayOf(
			doubleArrayOf(1.5, 0.0, 0.0), doubleArrayOf(0.0, 0.0, -1.5), doubleArrayOf(1.1, 0.0, 1.1), doubleArrayOf(-1.1, 0.0, 1.1),
			doubleArrayOf(0.0, 3.0, 0.0), doubleArrayOf(0.0, -3.0, 0.0), doubleArrayOf(1.0, 2.0, -1.0), doubleArrayOf(2.5, -1.5, 2.5),
		)
		for (rd in rds) {
			val g = Gaps()
			for (s in steps) {
				var x = 1000.3; var y = if (s[1] < 0) 16.0 * rd + 260 else 70.0; var z = -500.7
				val frames = if (rd == 32) 40 else 90
				for (f in 0 until frames) {
					val nx = x + s[0]; val ny = y + s[1]; val nz = z + s[2]
					if (f % 3 == 0) scan(g, nx, ny, nz, rd, Double.POSITIVE_INFINITY, ownsOnly = false, decideX = x, decideY = y, decideZ = z)
					x = nx; y = ny; z = nz
				}
			}
			println("rd $rd moving: ${g.hidden} hidden column-frames, gaps vanilla ${g.vanilla} cylinder ${g.cylinder}")
			assertEquals(0, g.vanilla, "moving vanilla gaps rd $rd")
			assertEquals(0, g.cylinder, "moving cylinder gaps rd $rd")
		}
	}

	@Test
	fun verticalRuleIsTheTighterOfTheSectionCountAndTheBlockDistance() {

		assertTrue(SurfaceHandoffGeometry.verticalOwns(32, 48, 160.0, 8))
		assertFalse(SurfaceHandoffGeometry.verticalOwns(16, 48, 160.0, 8))
		assertTrue(SurfaceHandoffGeometry.verticalOwns(32, 48, 175.9, 8))

		assertFalse(SurfaceHandoffGeometry.verticalOwns(288, 300, 160.0, 8))
		assertTrue(SurfaceHandoffGeometry.verticalOwns(288, 300, 160.5, 8))
		assertFalse(SurfaceHandoffGeometry.verticalOwns(32, 48, 176.0, 8))
		assertTrue(SurfaceHandoffGeometry.verticalOwns(64, 64, 9999.0, 8), "an empty range is trivially drawn")
		assertTrue(SurfaceHandoffGeometry.verticalEdge(32, 48, 160.0, 8), "the last section before the cut is pre-drawn")
		assertFalse(SurfaceHandoffGeometry.verticalEdge(64, 80, 100.0, 8))
		assertFalse(SurfaceHandoffGeometry.verticalOwns(64, 80, 100.0, 0))
	}

	@Test
	fun fogLimit() {
		assertEquals(128.0, SurfaceHandoffGeometry.limit(8))
		assertEquals(48.0, SurfaceHandoffGeometry.limit(8, 48.0))
		assertEquals(Float.POSITIVE_INFINITY, SurfaceFog.fogCullEnd(Float.NaN))
		assertEquals(Float.POSITIVE_INFINITY, SurfaceFog.fogCullEnd(0f))
		assertEquals(96f, SurfaceFog.fogCullEnd(96f))

		for (cx in -12..12) for (cz in -12..12)
			if (SurfaceHandoffGeometry.gameOwns(cx, cz, 8.5, 8.5, 10, 40.0)) assertTrue(SurfaceHandoffGeometry.gameOwns(cx, cz, 8.5, 8.5, 10))
	}

	@Test
	fun texturesAreLookedUpBeforeThePassNeverInsideIt() {
		val lookups = ArrayList<String>()
		var passOpen = false
		val tex = SurfacePassTextures<String, String>(listOf("blocks", "lightmap"))
		val lookup = { k: String -> check(!passOpen) { "texture looked up inside a pass" }; lookups.add(k); "view:$k" }
		assertNull(tex.inPass("blocks"), "nothing before prepare")
		assertTrue(tex.prepare(lookup))
		passOpen = true
		repeat(100) { assertEquals("view:blocks", tex.inPass("blocks")); assertEquals("view:lightmap", tex.inPass("lightmap")) }
		passOpen = false
		assertEquals(listOf("blocks", "lightmap"), lookups, "each texture resolved once, at prepare")
		assertNull(tex.inPass("other"))
		assertFalse(tex.prepare { k -> if (k == "lightmap") null else "v" }, "a missing texture means no draw")
		assertNull(tex.inPass("blocks"))
	}

	@Test
	fun rendererBindsOnlyPreResolvedTextures() {

		var root: java.io.File? = java.io.File("").absoluteFile
		while (root != null && !java.io.File(root, "src/main/kotlin/features/render/farterrain/surface/SurfaceRenderer.kt").isFile) root = root.parentFile
		val src = java.io.File(root ?: error("project root not found"), "src/main/kotlin/features/render/farterrain/surface/SurfaceRenderer.kt").readText()
		val bind = src.substringAfter("private fun bind(pass: RenderPass)").substringBefore("\n\t}\n")
		assertFalse(bind.contains("getTexture") || bind.contains("textureManager") || bind.contains("lightmap()"), bind)
		assertTrue(src.substringAfter("fun prepareFrame(").substringBefore("private fun bind").contains("resolveTextures()"))
	}

	@Test
	fun failuresAreLoggedOncePerCause() {
		val log = SurfaceErrorLog()
		fun boom(msg: String): Throwable = try { throw IllegalStateException(msg) } catch (t: Throwable) { t }
		val a1 = boom("Close the existing render pass")
		val a2 = boom("Close the existing render pass")
		assertTrue(log.firstTime(a1))
		assertFalse(log.firstTime(a2), "same cause again")
		assertTrue(log.firstTime(boom("another")), "a different message is a new cause")
		fun wrap(c: Throwable): Throwable = RuntimeException("Uploading texture", c)
		assertTrue(log.firstTime(wrap(a1)), "a wrapped cause is new")
		assertFalse(log.firstTime(wrap(a2)))
	}
}
