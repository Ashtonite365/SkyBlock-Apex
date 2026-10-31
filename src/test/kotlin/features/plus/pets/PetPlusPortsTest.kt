// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.pets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.pets.PetPlusPorts

class PetPlusPortsTest {
	@Test
	fun candy() {
		assertEquals(7, PetPlusPorts.candyUsed(listOf("Combat", "(7/10) Pet Candy Used")))
		assertNull(PetPlusPorts.candyUsed(listOf("Combat")))
	}
}

class PetNametagTest {
	@Test
	fun rewrite() {
		assertEquals("§6Ammonite", dev.ashtonite365.apex.features.plus.pets.PetNametag.rewrite("§8[§7Lv100§8] §6Ammonite", false, true))
		assertNull(dev.ashtonite365.apex.features.plus.pets.PetNametag.rewrite("§8[§7Lv99§8] §6Ammonite", false, true))
		assertEquals("§dEndermite§5 ✦", dev.ashtonite365.apex.features.plus.pets.PetNametag.rewrite("§8[§7Lv5§8] §dEndermite§5 ✦", true, false))
	}
}
