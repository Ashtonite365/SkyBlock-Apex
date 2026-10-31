// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.visualwords

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.visualwords.PlusLoreTweaks as L

class LoreExtrasTest {
	@Test
	fun chimera() {
		assertEquals("80%" to "60%", L.chimeraReplacement("Copies 80% of your active pet's stats."))
		assertEquals(null, L.chimeraReplacement("Sharpness VII"))
		assertTrue(L.isAprilFools(LocalDate.of(2026, 4, 1)))
		assertFalse(L.isAprilFools(LocalDate.of(2026, 10, 16)))
	}

	@Test
	fun stripUnits() {
		assertEquals("+50%", L.statIconReplacement("Crit Damage: +50%", true)!!.first)
		assertEquals("+50\uE007", L.statIconReplacement("Crit Damage: +50%", true)!!.second.drop(0).replace("§1", ""))
	}
}
