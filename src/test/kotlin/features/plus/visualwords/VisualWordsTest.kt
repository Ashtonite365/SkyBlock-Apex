// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.visualwords

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.visualwords.PlusLoreTweaks
import dev.ashtonite365.apex.features.plus.visualwords.VisualWords

class VisualWordsTest {
	@Test
	fun words() {
		val w = VisualWords.parse("Hyperion=>Hype;bad;=>x;Spirit=>Ghost")
		assertEquals(listOf("Hyperion" to "Hype", "Spirit" to "Ghost"), w)
		assertEquals("Hype and Ghost", VisualWords.apply("Hyperion and Spirit", w, true))
		assertEquals("Hype", VisualWords.apply("hyperion", w, false))
		assertEquals("hyperion", VisualWords.apply("hyperion", w, true))
	}

	@Test
	fun statIcons() {
		assertEquals("+30%" to "+30%§1\uE02C", PlusLoreTweaks.statIconReplacement("Crit Chance: +30%"))
		assertEquals("+60" to "+60§c\uE00D", PlusLoreTweaks.statIconReplacement("Strength: +60 (+20)"))
		assertNull(PlusLoreTweaks.statIconReplacement("Health: +1000❤"))
		assertNull(PlusLoreTweaks.statIconReplacement("Bogus Stat: +5"))
	}
}
