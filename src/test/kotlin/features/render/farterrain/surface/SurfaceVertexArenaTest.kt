// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBuffers
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFrameStream
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceVertexArena
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import kotlin.random.Random

class SurfaceVertexArenaTest {
	class Buf(val label: String, size: Long) { val bytes = ByteArray(size.toInt()); var closed = false }

	class FakeGpu : SurfaceBuffers<Buf> {
		val created = ArrayList<Buf>()
		var writes = 0; var copies = 0; var copiedBytes = 0L
		override fun create(label: String, bytes: Long) = Buf(label, bytes).also { created += it }
		override fun write(buffer: Buf, offset: Long, data: ByteBuffer) {
			check(!buffer.closed)
			val d = data.duplicate()
			check(offset + d.remaining() <= buffer.bytes.size) { "write past the end" }
			d.get(buffer.bytes, offset.toInt(), d.remaining())
			data.position(data.limit())
			writes++
		}
		override fun copy(from: Buf, to: Buf, bytes: Long) {
			check(!from.closed && !to.closed)
			System.arraycopy(from.bytes, 0, to.bytes, 0, bytes.toInt()); copies++; copiedBytes += bytes
		}
		override fun close(buffer: Buf) { check(!buffer.closed); buffer.closed = true }
		fun open() = created.count { !it.closed }
	}

	private fun vertices(n: Int, seed: Int): ByteBuffer {
		val b = ByteBuffer.allocateDirect(n * 28)
		val r = Random(seed)
		for (i in 0 until n * 28) b.put(i, r.nextInt().toByte())
		return b
	}

	@Test
	fun growsAndKeepsEveryRegionInPlace() {
		val gpu = FakeGpu()
		val arena = SurfaceVertexArena(gpu, 28, growStep = 28L * 1000, maxBytes = 1L shl 30)
		val placed = ArrayList<Triple<Int, Int, Int>>()
		val rnd = Random(3)
		repeat(200) { i ->
			val n = 50 + rnd.nextInt(900)
			val at = arena.allocate(n)
			assertTrue(at >= 0)
			arena.write(at, vertices(n, i))
			placed += Triple(at, n, i)
			if (rnd.nextInt(4) == 0) {
				val (s, u, _) = placed.removeAt(rnd.nextInt(placed.size))
				arena.free(s, u)
			}
		}
		assertTrue(arena.growths > 1, "it had to grow")
		assertEquals(1, gpu.open(), "one buffer, the old ones closed")
		val buf = arena.buffer!!
		for ((s, u, seed) in placed) {
			val want = vertices(u, seed)
			for (i in 0 until u * 28) assertEquals(want.get(i), buf.bytes[s * 28 + i], "region $seed byte $i moved or was lost")
		}
		assertEquals(placed.sumOf { it.second } * 28L, arena.usedBytes)
	}

	@Test
	fun refusesPastTheCapAndCloses() {
		val gpu = FakeGpu()
		val arena = SurfaceVertexArena(gpu, 28, growStep = 28L * 100, maxBytes = 28L * 250)
		assertEquals(0, arena.allocate(100))
		assertEquals(100, arena.allocate(100))
		assertEquals(-1, arena.allocate(100), "250 vertices max")
		assertEquals(1, arena.refused)
		assertEquals(200, arena.allocate(50), "what fits still fits")
		assertEquals(28L * 250, arena.capacityBytes)
		arena.close()
		assertEquals(0, gpu.open())
		assertEquals(0L, arena.capacityBytes)
		assertEquals(0, arena.allocate(10), "a fresh buffer after close")
	}

	@Test
	fun growthCopiesOnlyUpToTheHighestRange() {
		val gpu = FakeGpu()
		val arena = SurfaceVertexArena(gpu, 28, growStep = 28L * 100, maxBytes = 1L shl 30)
		val a = arena.allocate(60)
		arena.allocate(30)
		arena.free(a, 60)
		arena.allocate(80)
		assertEquals(2, arena.growths)
		assertEquals(90L * 28, gpu.copiedBytes, "copied the used part [0, 90), not the whole old buffer")
	}

	@Test
	fun frameStreamWritesOnlyChangesAndRotates() {
		val gpu = FakeGpu()
		val s = SurfaceFrameStream(gpu, "t", ring = 3, minBytes = 64)
		val data = SurfaceFrameStream.direct(256)
		for (i in 0 until 40) data.putInt(i * 4, i)
		val b1 = s.submit(data, 160)
		assertSame(b1, s.submit(data, 160), "unchanged: reused")
		assertEquals(1, s.writes); assertEquals(1, s.reuses)
		data.putInt(0, 99)
		val b2 = s.submit(data, 160)
		assertNotSame(b1, b2, "changed: the next ring buffer, never the one just used")
		data.putInt(0, 98)
		val b3 = s.submit(data, 160)
		data.putInt(0, 97)
		val b4 = s.submit(data, 160)
		assertSame(b1, b4, "three buffers in turn")
		assertNotSame(b2, b3)
		assertEquals(97, ByteBuffer.wrap(b4.bytes).order(java.nio.ByteOrder.nativeOrder()).getInt(0))
		assertEquals(0, data.position()); assertEquals(256, data.limit())
		assertFalse(b1.closed)

		val big = SurfaceFrameStream.direct(4096)
		val b5 = s.submit(big, 4000)
		assertTrue(b5.bytes.size >= 4000)
		assertEquals(3, gpu.open())
		s.close()
		assertEquals(0, gpu.open())
	}
}
