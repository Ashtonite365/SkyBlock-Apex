/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, block face culling.
 *
 * Far terrain's pipelines cull back faces on the GPU, so a face whose front points away from the camera is never seen.
 * The mesher therefore sorts every region's quads by the way they face (+X, -X, +Y, -Y, +Z, -Z, and "always" for
 * anything that is not a flat axis-aligned quad: fluids, crosses, rotated parts) into separate vertex ranges of the
 * same buffer, and remembers for each direction the nearest plane any of its quads lies on. Each frame a direction is
 * left out when every one of its quads faces away from the camera: +X when the camera is behind (west of) the
 * westernmost +X face plane, -X when it is east of the easternmost -X plane, and so on. That is conservative: a quad
 * that could face the camera is always drawn (camera inside the region, on a plane, or anywhere in between).
 * Meshing only; the saved chunk data is untouched.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.nio.ByteBuffer

object SurfaceFaceCull {
	const val POS_X = 0
	const val NEG_X = 1
	const val POS_Y = 2
	const val NEG_Y = 3
	const val POS_Z = 4
	const val NEG_Z = 5
	const val ALWAYS = 6
	const val BUCKETS = 7
	const val ALL = (1 shl BUCKETS) - 1

	const val MARGIN = 1e-3

	private const val PLANE_EPS = 1e-4f
	private const val AXIS_EPS = 1e-3f

	fun newPlanes(): FloatArray = FloatArray(6) { if (it % 2 == 0) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY }

	fun classify(xs: FloatArray, ys: FloatArray, zs: FloatArray, planes: FloatArray?): Int {

		var nx = 0f; var ny = 0f; var nz = 0f
		for (t in 0 until 2) {
			val a = 0; val b = 1 + t; val c = 2 + t
			val ux = xs[b] - xs[a]; val uy = ys[b] - ys[a]; val uz = zs[b] - zs[a]
			val vx = xs[c] - xs[a]; val vy = ys[c] - ys[a]; val vz = zs[c] - zs[a]
			nx += uy * vz - uz * vy
			ny += uz * vx - ux * vz
			nz += ux * vy - uy * vx
		}
		val ax = Math.abs(nx); val ay = Math.abs(ny); val az = Math.abs(nz)
		val len = ax + ay + az
		if (len <= 1e-9f) return ALWAYS
		val axis = when {
			ax > (ay + az) && ay + az <= AXIS_EPS * len -> 0
			ay > (ax + az) && ax + az <= AXIS_EPS * len -> 1
			az > (ax + ay) && ax + ay <= AXIS_EPS * len -> 2
			else -> return ALWAYS
		}
		val coords = when (axis) { 0 -> xs; 1 -> ys; else -> zs }
		val p = coords[0]
		for (i in 1 until 4) if (Math.abs(coords[i] - p) > PLANE_EPS) return ALWAYS
		val positive = when (axis) { 0 -> nx > 0; 1 -> ny > 0; else -> nz > 0 }
		val bucket = axis * 2 + if (positive) 0 else 1
		if (planes != null) {
			if (positive) { if (p < planes[bucket]) planes[bucket] = p } else { if (p > planes[bucket]) planes[bucket] = p }
		}
		return bucket
	}

	fun visibleMask(planes: FloatArray?, x: Double, y: Double, z: Double): Int {
		if (planes == null) return ALL
		var mask = 1 shl ALWAYS
		if (!(x < planes[POS_X] - MARGIN)) mask = mask or (1 shl POS_X)
		if (!(x > planes[NEG_X] + MARGIN)) mask = mask or (1 shl NEG_X)
		if (!(y < planes[POS_Y] - MARGIN)) mask = mask or (1 shl POS_Y)
		if (!(y > planes[NEG_Y] + MARGIN)) mask = mask or (1 shl NEG_Y)
		if (!(z < planes[POS_Z] - MARGIN)) mask = mask or (1 shl POS_Z)
		if (!(z > planes[NEG_Z] + MARGIN)) mask = mask or (1 shl NEG_Z)
		return mask
	}

	fun canFace(bucket: Int, p: Float, c: Double): Boolean = when (bucket) {
		POS_X, POS_Y, POS_Z -> c > p
		NEG_X, NEG_Y, NEG_Z -> c < p
		else -> true
	}

	private val scratch = ThreadLocal<ByteArray?>()
	private const val SCRATCH_KEEP = 16 shl 20

	private fun scratch(n: Int): ByteArray {
		val cur = scratch.get()
		if (cur != null && cur.size >= n) return cur
		if (n > SCRATCH_KEEP) return ByteArray(n)
		val grown = ByteArray(maxOf(n, minOf(SCRATCH_KEEP, (cur?.size ?: 0) * 2)))
		scratch.set(grown)
		return grown
	}

	fun reorder(buf: ByteBuffer, stride: Int, buckets: ByteArray, quads: Int) {
		val quadBytes = stride * 4
		val base = buf.position()
		val need = quads * quadBytes
		val src = scratch(need)
		buf.get(base, src, 0, need)
		var out = base
		val dst = buf.duplicate()
		for (b in 0 until BUCKETS) for (q in 0 until quads) {
			if (buckets[q].toInt() != b) continue
			dst.put(out, src, q * quadBytes, quadBytes)
			out += quadBytes
		}
	}
}
