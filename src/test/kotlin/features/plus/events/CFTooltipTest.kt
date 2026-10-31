// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.events.helpers.EventHelpers

class CFTooltipTest {
	@Test
	fun employee() {
		val l = EventHelpers.employeeTooltip(1, 1000.0, 400.0, 10.0)
		assertEquals("§7Extra: §6" + 2 + " §7choc/s", l[2])
		assertEquals("§7Effective Cost: §6500", l[3])
		assertEquals(3, EventHelpers.employeeTooltip(0, 10.0, null, null).size)
	}
}
