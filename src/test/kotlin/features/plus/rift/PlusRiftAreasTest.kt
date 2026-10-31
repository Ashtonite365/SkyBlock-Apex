package dev.ashtonite365.apex.test.features.plus.rift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.rift.PlusRiftAreas

class PlusRiftAreasTest {
	@Test
	fun vermin() {
		assertEquals("Silverfish", PlusRiftAreas.verminType("VERMIN VACUUMED! You vacuumed a Silverfish!"))
		assertEquals("Spider", PlusRiftAreas.verminType("VERMIN VACUUMED! You vacuumed a Spider! (+1 Vermin)"))
		assertNull(PlusRiftAreas.verminType("hello"))
	}
}
