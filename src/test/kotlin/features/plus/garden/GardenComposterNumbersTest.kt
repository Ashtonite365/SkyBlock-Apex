// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenMiscA

class GardenComposterNumbersTest {
	@Test
	fun numbers() {
		assertEquals("§662", GardenMiscA.composterNumber(13, listOf("Compost Available: 62")))
		val om = GardenMiscA.composterNumber(46, listOf("                     37,547.5/130k"))!!
		assertEquals("§e", om.take(2)); assertEquals("/130k", om.substringAfter('/').let { "/$it" })
		assertNull(GardenMiscA.composterNumber(10, listOf("Compost Available: 62")))
	}
}
