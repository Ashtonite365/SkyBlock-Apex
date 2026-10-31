// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings.regroup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.customchatcommands.ChatCommandsSettings
import dev.ashtonite365.apex.features.timers.TimerBoards
import dev.ashtonite365.apex.features.timers.TimerRouting
import dev.ashtonite365.apex.gui.config.DecimalHandler
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.render.WaypointLooks
import dev.ashtonite365.apex.util.render.WaypointStyle
import dev.ashtonite365.apex.util.render.WaypointType

@ExtendWith(AutoBootstrapExtension::class)
class RegroupHoldersTest {
	@Test fun decimalSnap() {
		val h = DecimalHandler(WaypointStyle.TConfig, 0.25, 4.0, 0.05)
		assertEquals(1.0, h.snap(0.99))
		assertEquals(0.25, h.snap(-3.0))
		assertEquals(4.0, h.snap(9.0))
		assertEquals(0.25, h.snap(Double.NaN))
		assertEquals(1.05, h.snap(1.04))
		assertEquals("1.05", h.label(1.05))
	}

	@Test fun waypointRows() {
		val looks = WaypointLooks.TConfig.allOptions.keys
		assertEquals(19 * 4, looks.size)
		for (t in WaypointType.entries) for (s in listOf("use-general", "shape", "opacity", "size")) assertTrue("${t.id}-$s" in looks, "${t.id}-$s")
		val style = WaypointStyle.TConfig.allOptions.keys
		for (k in listOf("shape", "total-opacity", "marker-size", "flat-face-player")) assertTrue(k in style, k)
	}

	@Test fun timerAndChatRows() {
		val keys = TimerBoards.TConfig.allOptions.keys
		for ((id, _) in TimerRouting.SHOW_ON) assertTrue("show-on-$id" in keys, id)
		for (k in listOf("board-2-enabled", "inventory-anchor", "inventory-theme", "inventory-background-opacity", "own-hud-positions"))
			assertTrue(k in keys, k)
		assertTrue("open-custom" in ChatCommandsSettings.TConfig.allOptions.keys)
	}
}
