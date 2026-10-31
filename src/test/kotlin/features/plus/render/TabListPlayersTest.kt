// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.CompactTabList

class TabListPlayersTest {
	@Test
	fun parse() {
		assertEquals(302 to "Ashton", CompactTabList.parsePlayer("[302] Ashton ♲"))
		assertEquals(null, CompactTabList.parsePlayer("Players (12)"))
		assertEquals("Ashton ♲", CompactTabList.cleanPlayerText("[302] Ashton ♲", true, false))
		assertEquals("[302] Ashton", CompactTabList.cleanPlayerText("[302] Ashton ♲", false, true))
	}
}
