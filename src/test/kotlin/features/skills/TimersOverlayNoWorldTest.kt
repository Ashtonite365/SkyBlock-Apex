// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.skills

import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.skills.TimersOverlay
import dev.ashtonite365.apex.util.mc.TabListReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class TimersOverlayNoWorldTest {
	@Test
	fun tickWithoutPlayerDoesNotThrow() {
		TabListReader.cachedSnapshot = null
		assertDoesNotThrow {
			for (tick in listOf(0, 20, 40)) TimersOverlay.onTick(TickEvent(tick))
			TabListReader.currentSnapshot
		}
	}
}
