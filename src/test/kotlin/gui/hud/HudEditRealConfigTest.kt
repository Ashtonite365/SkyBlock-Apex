// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.hud

import dev.ashtonite365.apex.gui.hud.editor.HudEditSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.fixes.Fixes
import dev.ashtonite365.apex.features.misc.Hud
import dev.ashtonite365.apex.gui.config.HudEnabled
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class HudEditRealConfigTest {
	init {

		listOf(Hud.TConfig, Fixes.TConfig).forEach { c -> if (c.sortedOptions.any { it._actualValue == null }) c.explicitDefaultLoad() }
	}

	@Test fun realLookupHidesDisabledAndShowsEnabled() {
		val day = Hud.TConfig.dayCountHud
		val before = Hud.TConfig.dayCount
		try {
			Hud.TConfig.dayCount = false
			assertFalse(day.isEnabled, "day count off: hidden")
			assertEquals(emptyList<Any>(), HudEditSession.visibleOf(listOf(day)))
			Hud.TConfig.dayCount = true
			assertTrue(day.isEnabled, "day count on: shown")
			assertEquals(listOf(day), HudEditSession.visibleOf(listOf(day)))
		} finally { Hud.TConfig.dayCount = before }
	}

	@Test fun unknownElementIsShownAndNeverThrows() {
		assertTrue(HudEnabled.isEnabled("apex.config.nope.nothing"))
		assertTrue(HudEnabled.isEnabled(null))
	}
}
