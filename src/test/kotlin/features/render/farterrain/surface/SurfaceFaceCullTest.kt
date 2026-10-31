// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.LayerAcc
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceDrawRuns.Run
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.ALL
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.ALWAYS
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.BUCKETS
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.NEG_X
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.NEG_Y
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.NEG_Z
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.POS_X
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.POS_Y
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFaceCull.POS_Z
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFog
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRegions
import net.minecraft.client.renderer.fog.FogData
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.random.Random

class SurfaceFaceCullTest {

	private class Quad(val xs: FloatArray, val ys: FloatArray, val zs: FloatArray) {

		fun frontFaces(cx: Double, cy: Double, cz: Double): Boolean {
			var nx = 0.0; var ny = 0.0; var nz = 0.0
			for (t in 0 until 2) {
				val b = 1 + t; val c = 2 + t
				val ux = (xs[b] - xs[0]).toDouble(); val uy = (ys[b] - ys[0]).toDouble(); val uz = (zs[b] - zs[0]).toDouble()
				val vx = (xs[c] - xs[0]).toDouble(); val vy = (ys[c] - ys[0]).toDouble(); val vz = (zs[c] - zs[0]).toDouble()
				nx += uy * vz - uz * vy; ny += uz * vx - ux * vz; nz += ux * vy - uy * vx
			}
			return nx * (cx - xs[0]) + ny * (cy - ys[0]) + nz * (cz - zs[0]) > 0
		}
	}

	private fun axisQuad(axis: Int, positive: Boolean, p: Float, u: Float, v: Float, hu: Float, hv: Float): Quad {

		val su = floatArrayOf(-hu, hu, hu, -hu)
		val sv = floatArrayOf(-hv, -hv, hv, hv)
		val xs = FloatArray(4); val ys = FloatArray(4); val zs = FloatArray(4)
		for (i in 0 until 4) {
			val a = u + su[i]
			val b = v + (if (positive) sv[i] else -sv[i])
			when (axis) {
				0 -> { xs[i] = p; ys[i] = a; zs[i] = b }
				1 -> { ys[i] = p; zs[i] = a; xs[i] = b }
				else -> { zs[i] = p; xs[i] = a; ys[i] = b }
			}
		}
		return Quad(xs, ys, zs)
	}

	private fun bucketOf(q: Quad, planes: FloatArray? = null) = SurfaceFaceCull.classify(q.xs, q.ys, q.zs, planes)

	@Test
	fun vanillaWindingGoesToItsOutwardBucket() {

		assertEquals(POS_Y, bucketOf(Quad(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(1f, 1f, 1f, 1f), floatArrayOf(0f, 1f, 1f, 0f))))
		assertEquals(NEG_Y, bucketOf(Quad(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0f, 0f, 0f, 0f), floatArrayOf(1f, 0f, 0f, 1f))))
		val expect = intArrayOf(POS_X, NEG_X, POS_Y, NEG_Y, POS_Z, NEG_Z)
		for (axis in 0 until 3) for (pos in listOf(true, false)) {
			val q = axisQuad(axis, pos, 3.5f, 1f, 2f, 0.5f, 0.25f)
			assertEquals(expect[axis * 2 + if (pos) 0 else 1], bucketOf(q), "axis $axis positive $pos")
			assertTrue(q.frontFaces(if (axis == 0) (if (pos) 10.0 else -10.0) else 1.0, if (axis == 1) (if (pos) 10.0 else -10.0) else 1.0, if (axis == 2) (if (pos) 10.0 else -10.0) else 1.0))
		}
	}

	@Test
	fun oddQuadsGoToAlways() {

		assertEquals(ALWAYS, bucketOf(Quad(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0f, 1f, 1f, 0f), floatArrayOf(0f, 0f, 1f, 1f))))
		assertEquals(ALWAYS, bucketOf(Quad(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(1f, 1f, 1.2f, 1f), floatArrayOf(0f, 1f, 1f, 0f))))
		assertEquals(ALWAYS, bucketOf(Quad(FloatArray(4), FloatArray(4), FloatArray(4))))

		assertEquals(ALWAYS, bucketOf(Quad(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0.9f, 0.88f, 0.8f, 0.85f), floatArrayOf(0f, 1f, 1f, 0f))))
		assertEquals(ALL, SurfaceFaceCull.visibleMask(null, 0.0, 0.0, 0.0))
	}

	private fun randomQuads(rnd: Random, n: Int): List<Quad> = List(n) {
		if (rnd.nextInt(10) == 0) {

			val x = rnd.nextFloat() * 128; val y = rnd.nextFloat() * 300; val z = rnd.nextFloat() * 128
			Quad(floatArrayOf(x, x, x + 1, x + 1), floatArrayOf(y, y + 1, y + 1, y), floatArrayOf(z, z, z + 1, z + 1))
		} else {
			val axis = rnd.nextInt(3)
			val size = if (axis == 1) 300f else 128f

			val p = if (rnd.nextBoolean()) rnd.nextInt(size.toInt() + 1).toFloat() else rnd.nextFloat() * size
			axisQuad(axis, rnd.nextBoolean(), p, rnd.nextFloat() * 128, rnd.nextFloat() * 128, 0.5f, 0.5f)
		}
	}

	@Test
	fun neverSkipsAFaceThatCanFaceTheCamera() {
		val rnd = Random(20261025)
		repeat(40) { round ->
			val quads = randomQuads(rnd, 1 + rnd.nextInt(200))
			val planes = SurfaceFaceCull.newPlanes()
			val buckets = quads.map { bucketOf(it, planes) }

			val cams = ArrayList<DoubleArray>()
			repeat(300) { cams.add(doubleArrayOf(rnd.nextDouble(-2000.0, 2000.0), rnd.nextDouble(-500.0, 900.0), rnd.nextDouble(-2000.0, 2000.0))) }
			repeat(300) { cams.add(doubleArrayOf(rnd.nextDouble(0.0, 128.0), rnd.nextDouble(0.0, 300.0), rnd.nextDouble(0.0, 128.0))) }
			for (q in quads.take(30)) for (d in doubleArrayOf(0.0, 1e-6, -1e-6, 1e-3, -1e-3, 0.01, -0.01)) {
				cams.add(doubleArrayOf(q.xs[0] + d, q.ys[0] + d, q.zs[0] + d))
				cams.add(doubleArrayOf(q.xs[0] + d, rnd.nextDouble(0.0, 300.0), rnd.nextDouble(0.0, 128.0)))
				cams.add(doubleArrayOf(rnd.nextDouble(0.0, 128.0), q.ys[0] + d, rnd.nextDouble(0.0, 128.0)))
				cams.add(doubleArrayOf(rnd.nextDouble(0.0, 128.0), rnd.nextDouble(0.0, 300.0), q.zs[0] + d))
			}
			for (c in cams) {
				val mask = SurfaceFaceCull.visibleMask(planes, c[0], c[1], c[2])
				assertTrue(mask and (1 shl ALWAYS) != 0)
				for (i in quads.indices) {
					if ((mask ushr buckets[i]) and 1 != 0) continue
					assertFalse(quads[i].frontFaces(c[0], c[1], c[2]), "round $round: skipped a quad facing camera ${c.toList()}")
				}
			}
		}
	}

	@Test
	fun cameraInsideTheRegionKeepsEveryDirection() {
		val planes = SurfaceFaceCull.newPlanes()

		for (axis in 0 until 3) for (pos in listOf(true, false)) {
			bucketOf(axisQuad(axis, pos, 0f, 5f, 5f, 1f, 1f), planes)
			bucketOf(axisQuad(axis, pos, 100f, 5f, 5f, 1f, 1f), planes)
		}
		assertEquals(ALL, SurfaceFaceCull.visibleMask(planes, 50.0, 50.0, 50.0))
		assertEquals(ALL, SurfaceFaceCull.visibleMask(planes, 0.0, 100.0, 0.0))
	}

	@Test
	fun outsideTerrainSkipsAboutHalfTheFaces() {

		val planes = SurfaceFaceCull.newPlanes()
		val perBucket = IntArray(BUCKETS)
		val rnd = Random(7)
		for (x in 0 until 128) for (z in 0 until 128) {
			val h = 60 + rnd.nextInt(10)
			val qs = listOf(
				axisQuad(1, true, h.toFloat(), z + 0.5f, x + 0.5f, 0.5f, 0.5f),
				axisQuad(1, false, 55f, z + 0.5f, x + 0.5f, 0.5f, 0.5f),
				axisQuad(0, true, x + 1f, h - 0.5f, z + 0.5f, 0.5f, 0.5f),
				axisQuad(0, false, x.toFloat(), h - 0.5f, z + 0.5f, 0.5f, 0.5f),
				axisQuad(2, true, z + 1f, x + 0.5f, h - 0.5f, 0.5f, 0.5f),
				axisQuad(2, false, z.toFloat(), x + 0.5f, h - 0.5f, 0.5f, 0.5f),
			)
			for (q in qs) perBucket[bucketOf(q, planes)]++
		}
		val total = perBucket.sum()
		assertEquals(0, perBucket[ALWAYS])

		val mask = SurfaceFaceCull.visibleMask(planes, 900.0, 200.0, -700.0)
		val skipped = (0 until BUCKETS).filter { (mask ushr it) and 1 == 0 }.sumOf { perBucket[it] }
		assertEquals(setOf(NEG_X, NEG_Y, POS_Z), (0 until BUCKETS).filter { (mask ushr it) and 1 == 0 }.toSet())
		assertTrue(skipped * 2 >= total, "skipped $skipped of $total")

		val above = SurfaceFaceCull.visibleMask(planes, 64.0, 200.0, 64.0)
		assertEquals(ALL and (1 shl NEG_Y).inv(), above)
		assertTrue(POS_X < BUCKETS && NEG_Z < BUCKETS && POS_Y < BUCKETS)
	}

	@Test
	fun runsLeaveOutCulledBucketsAndSkippedChunks() {
		val slots = SurfaceRegions.SLOTS
		val count = IntArray(BUCKETS * slots)
		count[POS_Y * slots + 0] = 8; count[POS_Y * slots + 1] = 4
		count[NEG_Y * slots + 0] = 12
		count[NEG_X * slots + 0] = 4
		count[ALWAYS * slots + 1] = 4
		val start = IntArray(count.size).also { var a = 0; for (i in it.indices) { it[i] = a; a += count[i] } }
		val skip = BooleanArray(slots)
		val all = SurfaceDrawRuns.bucketRuns(start, count, slots, ALL, skip)
		assertEquals(listOf(Run(0, 32)), all.runs)
		assertEquals(0, all.culledFaces)
		val noBottoms = SurfaceDrawRuns.bucketRuns(start, count, slots, ALL and (1 shl NEG_Y).inv(), skip)
		assertEquals(listOf(Run(0, 16), Run(28, 4)), noBottoms.runs)
		assertEquals(3, noBottoms.culledFaces)
		skip[0] = true
		val r = SurfaceDrawRuns.bucketRuns(start, count, slots, ALL and (1 shl NEG_Y).inv(), skip)
		assertEquals(listOf(Run(12, 4), Run(28, 4)), r.runs)
		assertEquals(0, r.culledFaces)
	}

	@Test
	fun reorderIsStableAndBucketMajor() {
		val stride = 4
		val buckets = byteArrayOf(POS_Y.toByte(), ALWAYS.toByte(), POS_X.toByte(), POS_Y.toByte(), NEG_Z.toByte(), POS_X.toByte())
		val buf = ByteBuffer.allocate(8 + buckets.size * 4 * stride)
		buf.position(8)
		for (q in buckets.indices) repeat(4 * stride) { buf.put((q * 10 + it / stride).toByte()) }
		buf.position(8)
		SurfaceFaceCull.reorder(buf, stride, buckets, buckets.size)
		assertEquals(8, buf.position())
		val order = (0 until buckets.size).map { buf.get(8 + it * 4 * stride) / 10 }
		assertEquals(listOf(2, 5, 0, 3, 4, 1), order)

		assertArrayEquals(byteArrayOf(20, 20, 20, 20, 21, 21, 21, 21, 22, 22, 22, 22, 23, 23, 23, 23), ByteArray(16).also { buf.get(8, it) })
	}

	@Test
	fun accumulatorCountsLooseVertexQuadsPerBucketAndSlot() {
		val a = LayerAcc()
		val up = axisQuad(1, true, 4f, 1f, 1f, 0.5f, 0.5f)
		for (i in 0 until 4) a.vertex(up.xs[i], up.ys[i], up.zs[i], 3)
		assertTrue(a.consistent)
		a.vertex(0f, 0f, 0f, 3)
		assertFalse(a.consistent)
		assertEquals(4, a.counter[POS_Y * SurfaceRegions.SLOTS + 3])
		assertEquals(1, a.quads)
		assertEquals(4f, a.planes[POS_Y])
	}

	@Test
	fun settingDefaultsOnAndOldOcclusionKeyIsIgnored() {
		val c = SurfaceManager.TConfig
		c.explicitDefaultLoad()
		assertTrue(c.faceCulling)
		assertFalse("generous-occlusion-culling" in c.allOptions)

		c.loadFrom(Unit, Json.parseToJsonElement("""{"apex-far-terrain":{"generous-occlusion-culling":false,"block-face-culling":false}}""").jsonObject)
		assertFalse(c.faceCulling)
		val saved = c.saveTo(Unit)["apex-far-terrain"]!!.jsonObject
		assertFalse("generous-occlusion-culling" in saved)
		c.explicitDefaultLoad()
		assertTrue(c.faceCulling)
	}

	@Test
	fun fogPushIsIdempotent() {
		val fog = FogData()
		fog.renderDistanceStart = 180f; fog.renderDistanceEnd = 192f; fog.environmentalEnd = 1024f
		SurfaceFog.fadeStart = 2000f; SurfaceFog.fadeEnd = 2500f
		try {
			SurfaceFog.adjust(fog, true, null)
			val once = floatArrayOf(fog.renderDistanceStart, fog.renderDistanceEnd, SurfaceFog.envScale)

			SurfaceFog.adjust(fog, true, null)
			assertArrayEquals(once, floatArrayOf(fog.renderDistanceStart, fog.renderDistanceEnd, SurfaceFog.envScale))
			assertEquals(2500f, fog.renderDistanceEnd)
			assertEquals(2000f, fog.renderDistanceStart)
		} finally {
			SurfaceFog.fadeStart = 0f; SurfaceFog.fadeEnd = 0f
			SurfaceFog.adjust(FogData(), false, null)
		}
	}
}
