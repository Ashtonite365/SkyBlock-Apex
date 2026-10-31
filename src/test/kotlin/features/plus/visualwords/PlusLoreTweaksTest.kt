// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.visualwords

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.visualwords.PlusLoreTweaks

class PlusLoreTweaksTest {
	@Test
	fun roman() {
		assertEquals(12, PlusLoreTweaks.romanToInt("XII"))
		assertEquals(null, PlusLoreTweaks.romanToInt("IIII"))
		assertEquals("Catacombs Level 12", PlusLoreTweaks.replaceRoman("Catacombs Level XII"))
		assertEquals("Progress to Tier 3: 0%", PlusLoreTweaks.replaceRoman("Progress to Tier III: 0%"))
		assertEquals("Heart of the Mountain 10", PlusLoreTweaks.replaceRoman("Heart of the Mountain X"))
		assertEquals("Sharpness VII", PlusLoreTweaks.replaceRoman("Sharpness VII"))
	}

	@Test
	fun hex() {
		assertEquals(listOf(0x702963), PlusLoreTweaks.findHexColours("Hex #702963"))
		assertEquals(emptyList<Int>(), PlusLoreTweaks.findHexColours("no colour"))
	}
}
