package dev.ashtonite365.apex.test.features.plus.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.commands.PlusCommandExtras
import dev.ashtonite365.apex.util.plus.matches

class PlusCommandExtrasTest {
	@Test
	fun viewRecipe() {
		assertEquals("viewrecipe ASPECT_OF_THE_END", PlusCommandExtras.fixViewRecipe("viewrecipe aspect of the end"))
		assertEquals("viewrecipe ASPECT_OF_THE_END 2", PlusCommandExtras.fixViewRecipe("viewrecipe aspect_of_the_end 2"))
		assertNull(PlusCommandExtras.fixViewRecipe("viewrecipe ASPECT_OF_THE_END"))
		assertNull(PlusCommandExtras.fixViewRecipe("warp hub"))
	}

	@Test
	fun storage() {
		assertNull(PlusCommandExtras.handleStorage("ec"))
		assertNull(PlusCommandExtras.handleStorage("ec 4"))
		assertEquals("enderchest 4", PlusCommandExtras.handleStorage("ec"))
		assertNull(PlusCommandExtras.handleStorage("bp"))
		assertNull(PlusCommandExtras.handleStorage("ec 40"))
		assertEquals("enderchest 4", PlusCommandExtras.handleStorage("enderchest"))
	}

	@Test
	fun cooldown() {
		assertTrue(PlusCommandExtras.cooldownPattern.matches("§cYou may only use this command after 4s on the server!"))
	}
}
