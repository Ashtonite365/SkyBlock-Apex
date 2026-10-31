/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceBuffers
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFrameSchedule
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFrameStream
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceVertexArena
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class SurfaceFrameScheduleTest {

	private class FakeEncoder : SurfaceBuffers<ByteArray> {
		var passOpen = false
		var writes = 0
		var copies = 0
		override fun create(label: String, bytes: Long) = ByteArray(bytes.toInt())
		override fun write(buffer: ByteArray, offset: Long, data: ByteBuffer) {
			check(!passOpen) { "Close the existing render pass before performing additional commands" }
			data.duplicate().get(buffer, offset.toInt(), data.remaining()); writes++
		}
		override fun copy(from: ByteArray, to: ByteArray, bytes: Long) {
			check(!passOpen) { "Close the existing render pass before performing additional commands" }
			System.arraycopy(from, 0, to, 0, bytes.toInt()); copies++
		}
		override fun close(buffer: ByteArray) {}
	}

	private class Far(val io: FakeEncoder, val frames: SurfaceFrameSchedule) {
		val arena = SurfaceVertexArena(io, 4, 64, 1 shl 20)
		val commands = SurfaceFrameStream(io, "commands")
		val instances = SurfaceFrameStream(io, "instances")
		var prepared = 0
		var skipped = 0
		var drawn = 0
		var frameReady = false
		fun prepareFrame(regionsToUpload: Int, seed: Int) {
			frameReady = false
			if (!frames.canWrite()) { skipped++; return }
			repeat(regionsToUpload) {
				val units = 40
				val start = arena.allocate(units)
				arena.write(start, ByteBuffer.allocate(units * 4))
			}
			val cmd = ByteBuffer.allocate(40).also { it.putInt(0, seed) }
			commands.submit(cmd, 40)
			instances.submit(ByteBuffer.allocate(12).also { it.putInt(0, seed) }, 12)
			prepared++
			frameReady = true
		}
		fun drawInPass() { check(io.passOpen); if (frameReady) drawn++ }
	}

	private fun frame(far: Far, prePassHook: Boolean, regions: Int, seed: Int) {
		val f = far.frames
		if (prePassHook && f.beforeMainPass()) far.prepareFrame(regions, seed)
		far.io.passOpen = true
		f.passOpened()
		far.drawInPass()
		far.io.passOpen = false
		if (f.endMain()) far.prepareFrame(regions, seed)
		f.frameDone()
	}

	@Test
	fun fakeEncoderReproducesTheCrashWhenPreparingInsideThePass() {
		val io = FakeEncoder()
		val arena = SurfaceVertexArena(io, 4, 64, 1 shl 20)
		io.passOpen = true
		val start = arena.allocate(40)
		assertThrows(IllegalStateException::class.java) { arena.write(start, ByteBuffer.allocate(160)) }
	}

	@Test
	fun everyWriteGrowthAndCopyHappensBeforeThePass() {
		val far = Far(FakeEncoder(), SurfaceFrameSchedule())
		for (i in 0 until 20) frame(far, prePassHook = true, regions = 3, seed = i)
		assertEquals(20, far.prepared)
		assertEquals(20, far.drawn)
		assertEquals(0, far.skipped)
		assertTrue(far.arena.growths > 1, "arena grew (copying) several times")
		assertTrue(far.io.copies > 0)
	}

	@Test
	fun withoutThePrePassHookTheFrameIsPreparedAfterThePassNeverInside() {
		val far = Far(FakeEncoder(), SurfaceFrameSchedule())
		for (i in 0 until 5) frame(far, prePassHook = false, regions = 2, seed = i)
		assertEquals(5, far.prepared)
		assertEquals(0, far.skipped)
	}

	@Test
	fun preparesOncePerFrame() {
		val s = SurfaceFrameSchedule()
		assertTrue(s.beforeMainPass())
		assertFalse(s.beforeMainPass())
		s.passOpened()
		assertFalse(s.canWrite())
		assertFalse(s.endMain())
		assertTrue(s.canWrite())
		s.frameDone()
		assertTrue(s.beforeMainPass())
	}

	@Test
	fun preparationReachedInsideThePassIsSkippedNotCrashed() {
		val s = SurfaceFrameSchedule()
		val far = Far(FakeEncoder(), s)
		far.io.passOpen = true
		s.passOpened()
		far.prepareFrame(2, 1)
		assertEquals(1, far.skipped)
		assertEquals(0, far.io.writes)
		far.io.passOpen = false
		assertTrue(s.endMain())
		far.prepareFrame(2, 1)
		assertEquals(1, far.prepared)
	}
}
