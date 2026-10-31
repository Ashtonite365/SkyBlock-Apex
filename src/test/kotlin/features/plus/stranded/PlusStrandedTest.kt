package dev.ashtonite365.apex.test.features.plus.stranded

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.stranded.PlusStranded

class PlusStrandedTest {
	@Test
	fun placeable() {
		assertTrue(PlusStranded.isPlaceable(listOf("Some NPC", "", "Click to set the location of this NPC!")))
		assertFalse(PlusStranded.isPlaceable(listOf("Location: [1, 70, -3]", "Click to set the location of this NPC!")))
		assertFalse(PlusStranded.isPlaceable(listOf("Click to talk")))
	}
}
