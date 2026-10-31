// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.crimson

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.crimson.CrimsonPlusPorts

class CrimsonPlusPortsTest {
	@Test
	fun parsers() {
		assertEquals("EXPLOSIVE", CrimsonPlusPorts.volcanoStatus(" Volcano: EXPLOSIVE"))
		assertEquals("Flaming Flower", CrimsonPlusPorts.pabloFlower("[NPC] Pablo: Bring me that Flaming Flower as soon as you can!"))
		assertEquals("Flaming Flower", CrimsonPlusPorts.pabloFlower("[NPC] Pablo: ✆ Could you bring me a Flaming Flower?"))
		assertNull(CrimsonPlusPorts.pabloFlower("[NPC] Pablo: hi"))
		assertTrue(CrimsonPlusPorts.isSirih("[NPC] Sirih: Oink."))
	}
}
