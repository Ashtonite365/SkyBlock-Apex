package dev.ashtonite365.apex.test.features.items

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.recipes.ViewerStartup

class ViewerStartupTest {
	private fun start(
		running: Boolean = false,
		inWorld: Boolean = true,
		reloading: Boolean = false,
		containerOpen: Boolean = false,
		ticks: Int = ViewerStartup.START_DELAY_TICKS,
		attempted: Boolean = false,
	) = ViewerStartup.shouldStart(running, inWorld, reloading, containerOpen, ticks, attempted)

	@Test
	fun noticeIsSuppressed() {
		assertTrue(ViewerStartup.SUPPRESS_RECIPE_SYNC_NOTICE)
	}

	@Test
	fun startsOnceAfterTheDelayWhenNoRecipePacketCame() {
		assertTrue(start())
		assertTrue(start(ticks = 10_000))
		assertFalse(start(ticks = ViewerStartup.START_DELAY_TICKS - 1), "waits for a real recipe packet first")
		assertFalse(start(ticks = 0))
	}

	@Test
	fun neverDoubleStartsOrRetries() {
		assertFalse(start(running = true), "the recipe packet already started it")
		assertFalse(start(attempted = true), "one attempt per level, no retry loop")
		assertFalse(start(reloading = true), "JEI restarts itself after a resource reload")
		assertFalse(start(containerOpen = true), "JEI's own screen fallback owns that case")
		assertFalse(start(inWorld = false), "JEI can't start without a level/player")
	}
}
