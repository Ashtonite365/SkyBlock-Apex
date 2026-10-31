package dev.ashtonite365.apex.features.debug

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrameProfilerTest {
	@AfterEach fun off() { FrameProfiler.enabled = false; FrameProfiler.reset() }

	@Test fun offRecordsNothing() {
		FrameProfiler.enabled = false
		val r = FrameProfiler.time(FrameProfiler.Phase.BLUR) { Thread.sleep(2); 7 }
		assertEquals(7, r)
		FrameProfiler.endFrame(1_000_000)
		assertEquals(0.0, FrameProfiler.avgMs[FrameProfiler.Phase.BLUR.ordinal])
	}

	@Test fun onAveragesPhaseTimesAndCalls() {
		FrameProfiler.enabled = true
		FrameProfiler.add(FrameProfiler.Phase.ITEMS, 4_000_000)
		FrameProfiler.add(FrameProfiler.Phase.ITEMS, 2_000_000)
		FrameProfiler.endFrame(10_000_000)
		assertEquals(6.0, FrameProfiler.avgMs[FrameProfiler.Phase.ITEMS.ordinal], 1e-9)
		assertEquals(2.0, FrameProfiler.avgCalls[FrameProfiler.Phase.ITEMS.ordinal], 1e-9)
		FrameProfiler.endFrame(30_000_000)
		assertEquals(6.0 * (1 - FrameProfiler.ALPHA), FrameProfiler.avgMs[FrameProfiler.Phase.ITEMS.ordinal], 1e-9)
		assertEquals(20.0, FrameProfiler.avgFrameMs, 1e-9)
		assertTrue(FrameProfiler.lines().first().contains("50 fps"))
	}

	@Test fun timeStillRecordsWhenBlockThrows() {
		FrameProfiler.enabled = true
		runCatching { FrameProfiler.time(FrameProfiler.Phase.TOOLTIP) { error("x") } }
		FrameProfiler.endFrame(1)
		assertEquals(1.0, FrameProfiler.avgCalls[FrameProfiler.Phase.TOOLTIP.ordinal], 1e-9)
	}
}
