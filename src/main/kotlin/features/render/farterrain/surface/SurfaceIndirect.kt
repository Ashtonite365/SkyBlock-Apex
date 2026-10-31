/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, one draw call per layer for all regions (pure Kotlin, unit-tested).
 *
 * With every region's vertices in the shared buffer ([SurfaceVertexArena]), a layer's ranges from ALL drawn regions
 * can go out as one indirect multi-draw: a list of draw commands in a GPU buffer (indices, instances, first index,
 * base vertex, first instance; five 32-bit words each, the layout the graphics APIs define), drawn by one call. Each
 * region is one "instance": its offset from the camera comes from a per-instance vertex attribute (RegionOffset,
 * [SurfaceInstances]) picked by the command's first instance, instead of a uniform set per region. [SurfaceCommandList]
 * builds this frame's commands for all layers into one reused direct buffer, layer after layer, in the same order the
 * per-region path drew them (opaque near first, translucent far first), so the result on screen is the same.
 *
 * Only used where the game itself draws its terrain this way ([SurfaceIndirect.allowed]); everything else keeps the
 * per-region path.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.nio.ByteBuffer

object SurfaceIndirect {

	const val COMMAND_BYTES = 20

	fun allowed(gameUsesIndirect: Boolean, maxDrawCount: Int, failedBefore: Boolean, allInArena: Boolean): Boolean =
		gameUsesIndirect && maxDrawCount >= 1 && !failedBefore && allInArena

	fun calls(commands: Int, maxPerCall: Int): Int = if (commands <= 0) 0 else (commands + maxPerCall - 1) / maxPerCall
}

class SurfaceInstances {
	var data: ByteBuffer = SurfaceFrameStream.direct(64 * BYTES)
		private set
	var count = 0
		private set

	fun clear() { count = 0 }

	fun add(x: Float, y: Float, z: Float): Int {
		val at = count * BYTES
		if (at + BYTES > data.capacity()) data = grow(data, at, at + BYTES)
		data.putFloat(at, x).putFloat(at + 4, y).putFloat(at + 8, z)
		return count++
	}

	fun x(i: Int) = data.getFloat(i * BYTES)
	fun y(i: Int) = data.getFloat(i * BYTES + 4)
	fun z(i: Int) = data.getFloat(i * BYTES + 8)

	val bytes: Int get() = count * BYTES

	companion object {
		const val BYTES = 12

		internal fun grow(old: ByteBuffer, used: Int, need: Int): ByteBuffer {
			val n = SurfaceFrameStream.direct(maxOf(need, old.capacity() * 2))
			for (i in 0 until used) n.put(i, old.get(i))
			return n
		}
	}
}

class SurfaceCommandList(layers: Int) {
	var data: ByteBuffer = SurfaceFrameStream.direct(256 * SurfaceIndirect.COMMAND_BYTES)
		private set
	var count = 0
		private set
	val layerFirst = IntArray(layers)
	val layerCount = IntArray(layers)
	private var open = -1

	fun clear() { count = 0; layerFirst.fill(0); layerCount.fill(0); open = -1 }

	fun beginLayer(layer: Int) { open = layer; layerFirst[layer] = count; layerCount[layer] = 0 }

	fun add(plan: SurfaceDrawPlan, vertexBase: Int, instance: Int) {
		check(open >= 0) { "no open layer" }
		val n = plan.drawCount
		val need = (count + n) * SurfaceIndirect.COMMAND_BYTES
		if (need > data.capacity()) data = SurfaceInstances.grow(data, count * SurfaceIndirect.COMMAND_BYTES, need)
		val counts = plan.indexCounts
		val bases = plan.baseVertices
		val d = data
		var at = count * SurfaceIndirect.COMMAND_BYTES
		for (r in 0 until n) {
			d.putInt(at, counts.get(r))
			d.putInt(at + 4, 1)
			d.putInt(at + 8, 0)
			d.putInt(at + 12, vertexBase + bases.get(r))
			d.putInt(at + 16, instance)
			at += SurfaceIndirect.COMMAND_BYTES
		}
		count += n
		layerCount[open] += n
	}

	fun endLayer() { open = -1 }

	val bytes: Int get() = count * SurfaceIndirect.COMMAND_BYTES

	fun word(i: Int, w: Int): Int = data.getInt(i * SurfaceIndirect.COMMAND_BYTES + w * 4)
}
