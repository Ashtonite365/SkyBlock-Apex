/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, the per-quad decision for near terrain (Apex Far Terrain never connects): texture axes
 * from the quad itself, the 8-neighbour mask from a lazy 3x3x3 neighbour cache, covered-neighbour suppression and the
 * stacked-pane rule. No client classes, no allocation per call.
 */
package dev.ashtonite365.apex.features.render.connected

import kotlin.math.abs
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CrossCollisionBlock
import net.minecraft.world.level.block.state.BlockState

fun interface StateLookup {
	fun state(x: Int, y: Int, z: Int): BlockState
}

class NeighbourCache {
	private val cells = arrayOfNulls<BlockState>(27)
	private var lookup: StateLookup = StateLookup { _, _, _ -> AIR }
	private var ox = 0
	private var oy = 0
	private var oz = 0

	var reads = 0
		private set

	private val joins = ByteArray(27)
	private var rule: ConnectRule? = null
	private var self: BlockState = AIR

	fun begin(lookup: StateLookup, x: Int, y: Int, z: Int) {
		this.lookup = lookup
		ox = x; oy = y; oz = z
		cells.fill(null)
		joins.fill(0)
		rule = null
		reads = 0
	}

	fun joins(rule: ConnectRule, self: BlockState, dx: Int, dy: Int, dz: Int): Boolean {
		if (rule !== this.rule || self !== this.self) {
			joins.fill(0)
			this.rule = rule; this.self = self
		}
		val i = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1)
		val j = joins[i]
		if (j != 0.toByte()) return j == 2.toByte()
		val r = ConnectRules.matches(rule, self, at(dx, dy, dz))
		joins[i] = if (r) 2 else 1
		return r
	}

	fun at(dx: Int, dy: Int, dz: Int): BlockState {
		val i = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1)
		cells[i]?.let { return it }
		reads++
		return lookup.state(ox + dx, oy + dy, oz + dz).also { cells[i] = it }
	}

	companion object {
		val AIR: BlockState = Blocks.AIR.defaultBlockState()
	}
}

object TexAxes {
	private val DIRS = Direction.entries.toTypedArray()

	fun up(packed: Int): Direction = DIRS[packed ushr 3]
	fun right(packed: Int): Direction = DIRS[packed and 7]

	private fun dominant(x: Float, y: Float, z: Float): Direction? {
		val ax = abs(x); val ay = abs(y); val az = abs(z)
		if (ax < 1e-6f && ay < 1e-6f && az < 1e-6f) return null
		return when {
			ax >= ay && ax >= az -> if (x > 0) Direction.EAST else Direction.WEST
			ay >= az -> if (y > 0) Direction.UP else Direction.DOWN
			else -> if (z > 0) Direction.SOUTH else Direction.NORTH
		}
	}

	fun derive(
		face: Direction,
		x0: Float, y0: Float, z0: Float, u0: Float, v0: Float,
		x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
		x2: Float, y2: Float, z2: Float, u2: Float, v2: Float,
	): Int {
		val du1 = u1 - u0; val dv1 = v1 - v0
		val du2 = u2 - u0; val dv2 = v2 - v0
		val det = du1 * dv2 - du2 * dv1
		if (abs(det) < 1e-12f) return -1
		val ex1 = x1 - x0; val ey1 = y1 - y0; val ez1 = z1 - z0
		val ex2 = x2 - x0; val ey2 = y2 - y0; val ez2 = z2 - z0
		val right = dominant(ex1 * dv2 - ex2 * dv1, ey1 * dv2 - ey2 * dv1, ez1 * dv2 - ez2 * dv1).let { d -> if (det < 0) d?.opposite else d } ?: return -1
		val down = dominant(ex2 * du1 - ex1 * du2, ey2 * du1 - ey1 * du2, ez2 * du1 - ez1 * du2).let { d -> if (det < 0) d?.opposite else d } ?: return -1
		val up = down.opposite
		if (up.axis == face.axis || right.axis == face.axis || up.axis == right.axis) return -1
		return up.ordinal shl 3 or right.ordinal
	}
}

object ConnectMask {

	@JvmStatic
	fun compute(
		n: NeighbourCache, rule: ConnectRule, self: BlockState, plane: Int,
		face: Direction, up: Direction, right: Direction, boundary: Boolean, arm: Direction?,
	): Int {
		var m = 0
		val fx = face.stepX; val fy = face.stepY; val fz = face.stepZ
		for (i in 0 until 8) {
			val bit = 1 shl i
			if (plane and bit == 0) continue
			val a = ConnectTiles.SLOT_UP[i]
			val b = ConnectTiles.SLOT_RIGHT[i]
			val dx = up.stepX * a + right.stepX * b
			val dy = up.stepY * a + right.stepY * b
			val dz = up.stepZ * a + right.stepZ * b
			if (!n.joins(rule, self, dx, dy, dz)) continue
			if (boundary && n.at(dx + fx, dy + fy, dz + fz).isSolidRender) continue
			if (rule.pane && dy != 0 && arm != null && !hasArm(n.at(dx, dy, dz), arm)) continue
			m = m or bit
		}
		return m
	}

	@JvmStatic
	fun hasArm(state: BlockState, side: Direction): Boolean {
		val p = CrossCollisionBlock.PROPERTY_BY_DIRECTION[side] ?: return false
		return state.getValueOrElse(p, false)
	}
}

object PaneParts {

	@JvmStatic
	fun armOf(cx: Float, cz: Float): Direction? {
		val ox = cx - 0.5f
		val oz = cz - 0.5f
		return when {
			abs(ox) < 0.1f && abs(oz) < 0.1f -> null
			abs(ox) >= abs(oz) -> if (ox > 0) Direction.EAST else Direction.WEST
			else -> if (oz > 0) Direction.SOUTH else Direction.NORTH
		}
	}

	@JvmStatic
	fun hiddenCap(n: NeighbourCache, rule: ConnectRule, self: BlockState, up: Boolean, arm: Direction?): Boolean {
		val dy = if (up) 1 else -1
		if (!n.joins(rule, self, 0, dy, 0)) return false
		return arm == null || ConnectMask.hasArm(n.at(0, dy, 0), arm)
	}
}

object ConnectCore {
	const val KEEP = 0
	const val DROP = -1

	@JvmStatic
	fun decide(n: NeighbourCache, rule: ConnectRule, self: BlockState, face: Direction, plane: Int, xyzuv: FloatArray): Int =
		decide(n, rule, self, plane, QuadGeom().set(face, xyzuv))

	@JvmStatic
	fun decide(n: NeighbourCache, rule: ConnectRule, self: BlockState, plane: Int, g: QuadGeom): Int {
		val face = g.face
		if (rule.pane && face.axis == Direction.Axis.Y) {
			return if (PaneParts.hiddenCap(n, rule, self, face == Direction.UP, g.arm)) DROP else KEEP
		}
		if (plane < 0) return KEEP
		val up = g.up ?: return KEEP
		val right = g.right ?: return KEEP
		val mask = ConnectMask.compute(n, rule, self, plane, face, up, right, g.boundary, if (rule.pane) g.arm else null)
		return ConnectTiles.tileOf(mask)
	}

	@JvmStatic
	fun onBoundary(face: Direction, xyzuv: FloatArray): Boolean {
		val axis = face.axis.ordinal
		val target = if (face.axisDirection == Direction.AxisDirection.POSITIVE) 1f else 0f
		for (i in 0 until 4) if (abs(xyzuv[i * 5 + axis] - target) > 1e-4f) return false
		return true
	}
}

class QuadGeom {
	var face: Direction = Direction.UP
		private set
	var up: Direction? = null
		private set
	var right: Direction? = null
		private set
	var boundary = false
		private set
	var arm: Direction? = null
		private set

	fun set(face: Direction, xyzuv: FloatArray): QuadGeom {
		this.face = face
		val axes = TexAxes.derive(
			face,
			xyzuv[0], xyzuv[1], xyzuv[2], xyzuv[3], xyzuv[4],
			xyzuv[5], xyzuv[6], xyzuv[7], xyzuv[8], xyzuv[9],
			xyzuv[10], xyzuv[11], xyzuv[12], xyzuv[13], xyzuv[14],
		)
		up = if (axes < 0) null else TexAxes.up(axes)
		right = if (axes < 0) null else TexAxes.right(axes)
		boundary = ConnectCore.onBoundary(face, xyzuv)
		val cx = (xyzuv[0] + xyzuv[5] + xyzuv[10] + xyzuv[15]) * 0.25f
		val cz = (xyzuv[2] + xyzuv[7] + xyzuv[12] + xyzuv[17]) * 0.25f
		arm = PaneParts.armOf(cx, cz)
		return this
	}
}
