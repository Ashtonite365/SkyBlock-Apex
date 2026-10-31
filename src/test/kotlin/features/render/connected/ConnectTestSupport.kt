// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.NeighbourCache
import dev.ashtonite365.apex.features.render.connected.StateLookup
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceStates
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

class MapLookup : StateLookup {
	val blocks = HashMap<Long, BlockState>()
	private fun key(x: Int, y: Int, z: Int) = (x.toLong() and 0x1FFFFF shl 42) or (y.toLong() and 0x1FFFFF shl 21) or (z.toLong() and 0x1FFFFF)
	fun set(x: Int, y: Int, z: Int, s: BlockState) { blocks[key(x, y, z)] = s }
	fun set(x: Int, y: Int, z: Int, s: String) = set(x, y, z, st(s))
	override fun state(x: Int, y: Int, z: Int): BlockState = blocks[key(x, y, z)] ?: NeighbourCache.AIR
}

fun st(s: String): BlockState = SurfaceStates.state(s)

fun cacheAt(l: StateLookup, x: Int, y: Int, z: Int) = NeighbourCache().also { it.begin(l, x, y, z) }

private fun Direction.v() = floatArrayOf(stepX.toFloat(), stepY.toFloat(), stepZ.toFloat())

fun faceQuad(
	face: Direction, up: Direction, right: Direction, depth: Float = 0f,
	u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f,
	centre: FloatArray? = null, half: Float = 0.5f,
): FloatArray {
	val f = face.v(); val uu = up.v(); val rr = right.v()
	val c = centre ?: FloatArray(3) { 0.5f + (0.5f - depth) * f[it] }
	val out = FloatArray(20)
	val uvs = arrayOf(0f to 0f, 0f to 1f, 1f to 1f, 1f to 0f)
	for ((i, uv) in uvs.withIndex()) {
		val (u, v) = uv
		for (k in 0 until 3) out[i * 5 + k] = c[k] + (u - 0.5f) * 2 * half * rr[k] + (0.5f - v) * 2 * half * uu[k]
		out[i * 5 + 3] = u0 + u * (u1 - u0)
		out[i * 5 + 4] = v0 + v * (v1 - v0)
	}
	return out
}
