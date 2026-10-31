/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, the shared far-terrain vertex buffer and the per-frame draw data streams (pure
 * Kotlin over a small buffer interface, unit-tested with a fake; the GPU side is SurfaceRenderer.GpuBuffers).
 *
 * [SurfaceVertexArena]: every resident region's vertices live in ONE GPU buffer, sub-allocated with
 * [SurfaceArenaAllocator] in whole vertices. A region gets one range for all its layers, written in place (no buffer
 * created per region and layer). When a mesh does not fit, the buffer grows (half again, at least [growStep]): a new,
 * larger buffer, the used part copied over on the GPU, the old one closed. Offsets never move, so nothing that points
 * into the arena has to change. It never shrinks (the island's far terrain comes back the same size) until [close].
 *
 * [SurfaceFrameStream]: small per-frame data (region offsets, indirect draw commands) in a ring of three buffers, so
 * a write never lands in a buffer an earlier frame may still be reading. Unchanged data (the camera and the drawn set
 * did not change) is not written again: the last slice is reused.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import java.nio.ByteBuffer
import java.nio.ByteOrder

interface SurfaceBuffers<B> {
	fun create(label: String, bytes: Long): B
	fun write(buffer: B, offset: Long, data: ByteBuffer)
	fun copy(from: B, to: B, bytes: Long)
	fun close(buffer: B)
}

class SurfaceVertexArena<B>(
	private val io: SurfaceBuffers<B>,
	val unitBytes: Int,

	val growStep: Long,

	val maxBytes: Long,
	private val label: String = "Apex far terrain vertices",
) {
	private var alloc = SurfaceArenaAllocator(0)

	var buffer: B? = null
		private set
	var growths = 0
		private set

	var grownCopyBytes = 0L
		private set

	var refused = 0
		private set

	val capacityBytes: Long get() = alloc.capacity.toLong() * unitBytes
	val usedBytes: Long get() = alloc.used.toLong() * unitBytes
	val liveRanges: Int get() = alloc.liveRanges
	val freeBlocks: Int get() = alloc.freeBlocks
	fun fragmentation(): Double = alloc.fragmentation()

	private val maxUnits: Long = minOf(Int.MAX_VALUE.toLong(), maxBytes / unitBytes)

	fun allocate(units: Int): Int {
		require(units > 0) { "units $units" }
		val at = alloc.alloc(units)
		if (at >= 0) return at
		if (!grow(units)) { refused++; return -1 }
		return alloc.alloc(units)
	}

	fun free(start: Int, units: Int) = alloc.free(start, units)

	fun write(start: Int, data: ByteBuffer) {
		val b = buffer ?: error("arena has no buffer")
		io.write(b, start.toLong() * unitBytes, data)
	}

	private fun grow(units: Int): Boolean {
		val cap = alloc.capacity.toLong()
		val high = alloc.highWater().toLong()
		val need = high + units
		var next = maxOf(cap + cap / 2, cap + growStep / unitBytes, need)
		if (next > maxUnits) next = maxUnits
		if (next < need || next <= cap) return false
		val created = io.create(label, next * unitBytes)
		val old = buffer
		if (old != null) {
			val bytes = high * unitBytes
			if (bytes > 0) io.copy(old, created, bytes)
			grownCopyBytes += bytes
			io.close(old)
		}
		buffer = created
		alloc.grow(next.toInt())
		growths++
		return true
	}

	fun close() {
		buffer?.let { io.close(it) }
		buffer = null
		alloc = SurfaceArenaAllocator(0)
	}
}

class SurfaceFrameStream<B>(
	private val io: SurfaceBuffers<B>,
	private val label: String,
	private val ring: Int = 3,
	private val minBytes: Int = 4096,
) {
	private val bufs = ArrayList<B?>(ring).apply { repeat(ring) { add(null) } }
	private val sizes = LongArray(ring)
	private var slot = -1
	private var lastLen = -1
	private var prev: ByteBuffer = direct(minBytes)

	var writes = 0L
		private set
	var reuses = 0L
		private set
	var bytesWritten = 0L
		private set

	fun submit(data: ByteBuffer, len: Int): B {
		require(len > 0 && len <= data.capacity()) { "len $len" }
		val cur = if (slot >= 0) bufs[slot] else null
		if (cur != null && len == lastLen && same(data, len)) { reuses++; return cur }
		slot = (slot + 1) % ring
		var b = bufs[slot]
		if (b == null || sizes[slot] < len) {
			b?.let { io.close(it) }
			val size = maxOf(minBytes.toLong(), len.toLong() + len / 2).let { (it + 255) and 255L.inv() }
			b = io.create(label, size)
			bufs[slot] = b
			sizes[slot] = size
		}
		val pos = data.position(); val lim = data.limit()
		data.limit(len).position(0)
		io.write(b!!, 0L, data)
		data.limit(len).position(0)
		if (prev.capacity() < len) prev = direct(len + len / 2)
		prev.clear()
		prev.put(data)
		data.limit(lim).position(pos)
		lastLen = len
		writes++
		bytesWritten += len
		return b
	}

	private fun same(data: ByteBuffer, len: Int): Boolean {
		val pos = data.position(); val lim = data.limit()
		data.limit(len).position(0)
		prev.limit(len).position(0)
		val eq = data.mismatch(prev) == -1
		data.limit(lim).position(pos)
		prev.clear()
		return eq
	}

	fun invalidate() { lastLen = -1 }

	fun close() {
		for (i in 0 until ring) { bufs[i]?.let { io.close(it) }; bufs[i] = null; sizes[i] = 0 }
		slot = -1; lastLen = -1
	}

	companion object {
		fun direct(n: Int): ByteBuffer = ByteBuffer.allocateDirect(maxOf(n, 16)).order(ByteOrder.nativeOrder())
	}
}
