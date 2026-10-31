// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.crimson

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.mining.NucleusBarriersBox

class SulphurSkitterBoxTest {
	@Test
	fun nucleusBoxes() {
		val b = NucleusBarriersBox.box(474, 111, 524)
		assertEquals(486.0, b.maxX); assertEquals(125.0, b.maxY)
		assertEquals(5, NucleusBarriersBox.Crystal.entries.size)
	}
}
