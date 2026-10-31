/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.test.util.render

import dev.ashtonite365.apex.util.render.WorldBatchFlush
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldBatchFlushTest {
	class FakeDraw(val vertices: Int, val indices: Int = vertices / 4 * 6)
	class Info(val draw: FakeDraw)

	class FakeStaging : WorldBatchFlush.Staging<FakeDraw, Info> {
		val draws = ArrayList<FakeDraw>()
		var uploaded = false
		var passOpen = false
		val log = ArrayList<String>()
		private fun noPass(what: String) = check(!passOpen) { "Close the existing render pass before performing additional commands ($what)" }
		fun append(d: FakeDraw) { check(!uploaded) { "Cannot append draw after upload" }; draws += d }
		override fun requestIndexCount(draw: FakeDraw) { noPass("requestIndexCount"); log += "request" }
		override fun upload() {
			check(!uploaded) { "Already uploaded" }
			noPass("upload")
			if (draws.isEmpty() || draws.all { it.vertices == 0 }) return
			uploaded = true; log += "upload"
		}
		override fun executeInfo(draw: FakeDraw): Info? {
			if (draw.vertices == 0) return null
			check(uploaded && draw in draws) { "Cannot execute before upload" }
			return Info(draw)
		}
		override fun indexCount(info: Info) = info.draw.indices
		override fun resizeIndexBuffers() { noPass("resize"); log += "resize" }
		override fun endDraw() { draws.clear(); uploaded = false; log += "endDraw" }
		override fun endFrame() { endDraw(); log += "endFrame" }
	}

	class FakeDrawer(val staging: FakeStaging) : WorldBatchFlush.Drawer<FakeDraw, Info, String, String> {
		val issued = ArrayList<FakeDraw>()
		var opened = 0
		override fun prepare(draw: FakeDraw, info: Info): String { check(!staging.passOpen) { "uniform write inside pass" }; return "t" }
		override fun openPass(): String { staging.passOpen = true; opened++; return "pass" }
		override fun issue(pass: String, draw: FakeDraw, info: Info, prepared: String) { check(staging.passOpen); issued += draw }
		override fun closePass(pass: String) { staging.passOpen = false }
	}

	@Test
	fun fakeReproducesTheCrashWhenEndDrawRunsBeforeUpload() {

		val s = FakeStaging()
		val d = FakeDraw(4)
		s.append(d)
		s.endDraw()
		s.upload()
		assertThrows(IllegalStateException::class.java) { s.executeInfo(d) }
	}

	@Test
	fun everyDrawIsUploadedBeforeItIsExecutedAndOnlyDrawsHappenInThePass() {
		val s = FakeStaging()
		val draws = listOf(FakeDraw(4), FakeDraw(8), FakeDraw(12))
		draws.forEach(s::append)
		val drawer = FakeDrawer(s)
		assertEquals(3, WorldBatchFlush.flush(draws, s, drawer))
		assertEquals(draws, drawer.issued)
		assertEquals(1, drawer.opened)
		assertFalse(s.passOpen)
		assertEquals(listOf("request", "request", "request", "upload", "resize", "endDraw", "endDraw", "endFrame"), s.log)
		assertTrue(s.draws.isEmpty() && !s.uploaded, "staging reset for the next frame")
	}

	@Test
	fun emptyDrawsAreSkippedAndAnAllEmptyFrameOpensNoPass() {
		val s = FakeStaging()
		val draws = listOf(FakeDraw(0), FakeDraw(0))
		draws.forEach(s::append)
		val drawer = FakeDrawer(s)
		assertEquals(0, WorldBatchFlush.flush(draws, s, drawer))
		assertEquals(0, drawer.opened)

		val mixed = listOf(FakeDraw(0), FakeDraw(4), FakeDraw(4, indices = 0))
		mixed.forEach(s::append)
		val d2 = FakeDrawer(s)
		assertEquals(1, WorldBatchFlush.flush(mixed, s, d2))
		assertEquals(listOf(mixed[1]), d2.issued)
	}

	@Test
	fun noDrawsTouchesNothingButStillResets() {
		val s = FakeStaging()
		assertEquals(0, WorldBatchFlush.flush(emptyList(), s, FakeDrawer(s)))
		assertEquals(listOf("endDraw", "endDraw", "endFrame"), s.log)
	}

	@Test
	fun stagingIsResetEvenWhenADrawFails() {
		val s = FakeStaging()
		val draws = listOf(FakeDraw(4))
		draws.forEach(s::append)
		val failing = object : WorldBatchFlush.Drawer<FakeDraw, Info, String, String> {
			override fun prepare(draw: FakeDraw, info: Info) = "t"
			override fun openPass(): String { s.passOpen = true; return "p" }
			override fun issue(pass: String, draw: FakeDraw, info: Info, prepared: String) { error("boom") }
			override fun closePass(pass: String) { s.passOpen = false }
		}
		assertThrows(IllegalStateException::class.java) { WorldBatchFlush.flush(draws, s, failing) }
		assertFalse(s.passOpen)
		assertFalse(s.uploaded)

		val next = listOf(FakeDraw(4)); next.forEach(s::append)
		assertEquals(1, WorldBatchFlush.flush(next, s, FakeDrawer(s)))
	}
}
