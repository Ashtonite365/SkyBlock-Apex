/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, per-chunk frustum culling inside far regions (pure Kotlin, unit-tested).
 *
 * The renderer tests each 8x8-chunk region against the game's cull frustum first (unchanged). A region that lies only
 * partly on screen (it straddles a screen edge, or reaches behind the camera) was drawn whole; with this, each of its
 * chunks is tested on its own and a chunk that lies wholly off screen is left out.
 *
 * Own plane test: the four side planes (left, right, bottom, top) of the projection x view-rotation matrix, in
 * camera-relative coordinates. The near and far planes are not used (they depend on the depth convention, and the far
 * distance is handled elsewhere); the four side planes alone already exclude everything behind the camera. A chunk is
 * skipped only when its box (its own lowest to highest drawn block, grown by [SLACK] block on every side for models
 * and fluids that reach past their block) is wholly outside one plane even after that plane is tilted outwards by
 * [ANGLE_MARGIN] radians (view bobbing and small differences between the cull and the drawn projection). Frames where
 * the drawn view can differ a lot from the matrix (hurt / death tilt, nausea or portal warp, a captured debug
 * frustum, panoramic screenshots) do not use it at all: the caller skips the per-chunk test then.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import org.joml.Matrix4f
import org.joml.Matrix4fc

class SurfaceChunkFrustum {

	private val planes = DoubleArray(16)

	var valid = false
		private set

	private val tmp = Matrix4f()

	fun set(projection: Matrix4fc, viewRotation: Matrix4fc): Boolean {
		val m = tmp.set(projection).mul(viewRotation)

		val r0x = m.m00().toDouble(); val r0y = m.m10().toDouble(); val r0z = m.m20().toDouble(); val r0w = m.m30().toDouble()
		val r1x = m.m01().toDouble(); val r1y = m.m11().toDouble(); val r1z = m.m21().toDouble(); val r1w = m.m31().toDouble()
		val r3x = m.m03().toDouble(); val r3y = m.m13().toDouble(); val r3z = m.m23().toDouble(); val r3w = m.m33().toDouble()
		valid = plane(0, r3x + r0x, r3y + r0y, r3z + r0z, r3w + r0w) &&
			plane(1, r3x - r0x, r3y - r0y, r3z - r0z, r3w - r0w) &&
			plane(2, r3x + r1x, r3y + r1y, r3z + r1z, r3w + r1w) &&
			plane(3, r3x - r1x, r3y - r1y, r3z - r1z, r3w - r1w)
		return valid
	}

	fun setPlanes(p: DoubleArray): Boolean {
		require(p.size == 16)
		valid = (0 until 4).all { plane(it, p[it * 4], p[it * 4 + 1], p[it * 4 + 2], p[it * 4 + 3]) }
		return valid
	}

	fun invalidate() { valid = false }

	private fun plane(i: Int, a: Double, b: Double, c: Double, d: Double): Boolean {
		val len = Math.sqrt(a * a + b * b + c * c)
		if (!(len > 1e-9) || !len.isFinite() || !d.isFinite()) return false
		planes[i * 4] = a / len; planes[i * 4 + 1] = b / len; planes[i * 4 + 2] = c / len; planes[i * 4 + 3] = d / len
		return true
	}

	fun fullyInside(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean {
		if (!valid) return false
		for (i in 0 until 4) {
			val a = planes[i * 4]; val b = planes[i * 4 + 1]; val c = planes[i * 4 + 2]; val d = planes[i * 4 + 3]

			val x = if (a >= 0) minX else maxX
			val y = if (b >= 0) minY else maxY
			val z = if (c >= 0) minZ else maxZ
			if (a * x + b * y + c * z + d < 0) return false
		}
		return true
	}

	fun outside(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, angleMargin: Double = ANGLE_MARGIN): Boolean {
		if (!valid) return false

		val fx = Math.max(Math.abs(minX), Math.abs(maxX))
		val fy = Math.max(Math.abs(minY), Math.abs(maxY))
		val fz = Math.max(Math.abs(minZ), Math.abs(maxZ))
		val reach = Math.sqrt(fx * fx + fy * fy + fz * fz) * angleMargin
		for (i in 0 until 4) {
			val a = planes[i * 4]; val b = planes[i * 4 + 1]; val c = planes[i * 4 + 2]; val d = planes[i * 4 + 3]

			val x = if (a >= 0) maxX else minX
			val y = if (b >= 0) maxY else minY
			val z = if (c >= 0) maxZ else minZ
			if (a * x + b * y + c * z + d + reach < 0) return true
		}
		return false
	}

	fun chunkOutside(cx: Int, cz: Int, minY: Int, maxY: Int, camX: Double, camY: Double, camZ: Double): Boolean {
		if (!valid || maxY <= minY) return false
		val x0 = cx * 16.0 - SLACK - camX
		val z0 = cz * 16.0 - SLACK - camZ
		return outside(x0, minY - SLACK - camY, z0, x0 + 16 + 2 * SLACK, maxY + SLACK - camY, z0 + 16 + 2 * SLACK)
	}

	fun markRegion(
		rx: Int, rz: Int, minY: Int, maxY: Int, slotMinY: IntArray, slotMaxY: IntArray, skipSlot: BooleanArray,
		outOffScreen: BooleanArray, camX: Double, camY: Double, camZ: Double,
	): Int {
		java.util.Arrays.fill(outOffScreen, false)
		if (!valid) return 0
		val size = SurfaceRegions.SIZE * 16.0
		val bx = rx * size
		val bz = rz * size
		if (fullyInside(bx - SLACK - camX, minY - SLACK - camY, bz - SLACK - camZ, bx + size + SLACK - camX, maxY + SLACK - camY, bz + size + SLACK - camZ)) return 0
		var n = 0
		for (slot in 0 until SurfaceRegions.SLOTS) {
			if (skipSlot[slot]) continue
			val cx = rx * SurfaceRegions.SIZE + (slot % SurfaceRegions.SIZE)
			val cz = rz * SurfaceRegions.SIZE + (slot / SurfaceRegions.SIZE)
			if (chunkOutside(cx, cz, slotMinY[slot], slotMaxY[slot], camX, camY, camZ)) { outOffScreen[slot] = true; n++ }
		}
		return n
	}

	companion object {

		fun use(setting: Boolean, safeFrame: Boolean): Boolean = setting && safeFrame

		fun safeFrame(
			hurtTime: Float, deathTime: Float, deadOrDying: Boolean, portalEffect: Float, nauseaEffect: Float,
			frustumCaptured: Boolean, panoramic: Boolean,
		): Boolean = !(hurtTime > 0f) && !(deathTime > 0f) && !deadOrDying && !(portalEffect > 0f) && !(nauseaEffect > 0f) &&
			!frustumCaptured && !panoramic

		const val SLACK = 1.0

		const val ANGLE_MARGIN = 0.035
	}
}
