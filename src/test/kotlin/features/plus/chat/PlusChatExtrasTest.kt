// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.chat.PlusChatExtras

class PlusChatExtrasTest {
	@Test
	fun deathAndXp() {
		assertEquals("ZeroHazel", PlusChatExtras.deathName(" ☠ ZeroHazel was killed by Ashfang."))
		assertNull(PlusChatExtras.deathName(" ☠ You fell into the void."))
		assertNull(PlusChatExtras.deathName("hello"))
		assertEquals("§b+5 SkyBlock XP §7(Accessory Bag)§b (12/100)",
			PlusChatExtras.xpSegment("§c100/100❤     §b+5 SkyBlock XP §7(Accessory Bag)§b (12/100)     §b50/50✎"))
	}
}
