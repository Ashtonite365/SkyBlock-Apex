package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.pets.PetCareParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PetTraining1042Test {
	@Test fun fannPage() {
		val d = RealDumpFixtures.load("pet-training-1042")
		val p = PetCareParser.parse(d.title, d.slots, PetCareParser.TRAINING, 54)!!
		val g = PetCareParser.groups(p).associate { it.name to it.tiles }

		assertEquals(listOf("Training Slot #1"), g.getValue(PetCareParser.SLOTS).map { it.label })
		assertEquals(listOf("Training Slot #2", "Training Slot #3"), g.getValue(PetCareParser.LOCKED).map { it.label })

		assertNull(g[PetCareParser.PETS])
		assertEquals(31, g.getValue(PetCareParser.PREVIEW).single().slot)
		assertTrue(g.getValue(PetCareParser.INFO).any { it.label == "Extra Free Training" })
	}
}
