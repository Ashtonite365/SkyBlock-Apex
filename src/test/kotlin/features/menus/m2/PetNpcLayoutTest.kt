package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.pets.CellKind
import dev.ashtonite365.apex.features.menus.pets.OfferPetsMenu
import dev.ashtonite365.apex.features.menus.pets.PetSitterMenu
import dev.ashtonite365.apex.features.menus.pets.PetTrainingMenu
import dev.ashtonite365.apex.features.menus.pets.PetsMenu
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.pets.PetCareLayout
import dev.ashtonite365.apex.features.menus.pets.PetCareModel
import dev.ashtonite365.apex.features.menus.pets.PetCareParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PetNpcLayoutTest {
	private fun model(fx: String, regex: Regex, chest: Int, edit: (List<SlotText>) -> List<SlotText> = { it }): PetCareModel {
		val d = RealDumpFixtures.load(fx); val slots = edit(d.slots)
		return PetCareModel(PetCareParser.parse(d.title, slots, regex, chest)!!, slots, chest)
	}
	private fun layout(m: PetCareModel) = PetCareLayout.cells(m).associate { it.slot to it.kind }

	@Test fun petSitterHypixelLayout() {
		val m = model("pet-sitter-1042", PetCareParser.SITTER, 45)
		assertEquals(mapOf(13 to CellKind.INPUT, 22 to CellKind.BUTTON, 40 to CellKind.BUTTON), layout(m))
		val c = PetCareLayout.cells(m).associateBy { it.slot }
		assertEquals(1 to 4, c.getValue(13).row to c.getValue(13).col); assertEquals(2 to 4, c.getValue(22).row to c.getValue(22).col)
		assertEquals(4 to 4, c.getValue(40).row to c.getValue(40).col); assertEquals(5, PetCareLayout.rows(45))

		val placed = model("pet-sitter-1042", PetCareParser.SITTER, 45) { it + SlotText(13, "[Lvl 12] Rabbit", listOf("Farming Pet")) }
		assertEquals(CellKind.INPUT, layout(placed)[13])
	}

	@Test fun georgeHypixelLayout() {
		val m = model("offer-pets", PetCareParser.OFFER, 45)
		assertEquals(mapOf(13 to CellKind.INPUT, 22 to CellKind.BUTTON, 40 to CellKind.BUTTON, 41 to CellKind.BUTTON), layout(m))
	}

	@Test fun fannHypixelLayout() {
		for (fx in listOf("pet-training-fann", "pet-training-1042")) {
			val m = model(fx, PetCareParser.TRAINING, 54)
			assertEquals(mapOf(11 to CellKind.BUTTON, 13 to CellKind.BUTTON, 15 to CellKind.BUTTON, 31 to CellKind.PREVIEW,
				48 to CellKind.BUTTON, 49 to CellKind.BUTTON), layout(m), fx)
			assertTrue(PetCareLayout.cells(m).none { it.kind == CellKind.INPUT })
			assertEquals(6, PetCareLayout.rows(54))
		}
	}

	@Test fun inventoryOptIn() {
		for (menu in listOf(PetSitterMenu, OfferPetsMenu, PetTrainingMenu))
			assertTrue(menu.defaultSize.second >= 22 + PetCareLayout.rows(54) * 18 + InventoryPanel.HEIGHT, menu.id)
		assertEquals(300 + InventoryPanel.RESERVED, PetsMenu.defaultSize.second)
		assertEquals(220 + InventoryPanel.RESERVED, dev.ashtonite365.apex.features.menus.wardrobe.WardrobeMenu.defaultSize.second)
		assertEquals(45, InventoryPanel.slotIndex(0, 0, PetCareParser.chestSize(81)))
		assertEquals(54, InventoryPanel.slotIndex(0, 0, PetCareParser.chestSize(90)))
	}
}
