package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.minion.MinionChestMenu
import dev.ashtonite365.apex.features.menus.minion.MinionChestParser
import dev.ashtonite365.apex.features.menus.pets.OfferPetsMenu
import dev.ashtonite365.apex.features.menus.pets.PetCareParser
import dev.ashtonite365.apex.features.menus.pets.PetSitterMenu
import dev.ashtonite365.apex.features.menus.pets.PetTrainingMenu
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PetCareMinionChestTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun petSitter() {
		val d = RealDumpFixtures.load("pet-sitter")
		val p = PetCareParser.parse(d.title, d.slots, PetCareParser.SITTER, PetCareParser.chestSize(81))!!
		assertEquals("Pet Sitter", p.header?.cleanName); assertEquals(40, p.nav.close)
		assertTrue(p.items.isEmpty())
	}

	@Test fun petTrainingGeneric() {
		val d = RealDumpFixtures.load("pet-training")
		val p = PetCareParser.parse(d.title, d.slots, PetCareParser.TRAINING, PetCareParser.chestSize(90))!!
		val g = PetCareParser.groups(p).associate { it.name to it.tiles }
		assertEquals(listOf("Training Slot #1"), g.getValue(PetCareParser.SLOTS).map { it.label })
		assertEquals(2, g.getValue(PetCareParser.LOCKED).size)
		assertEquals(listOf("Owl"), g.getValue(PetCareParser.PREVIEW).map { it.label.removePrefix("[Lvl 100] ") }.map { it.substringAfterLast(' ') })

		val mod = d.slots.map { if (it.cleanName == "Extra Free Training") it.copy(name = "Renamed Info") else it } +
			SlotText(14, "Training Slot #9", listOf("Training", "", "Time left: 2h 5m"))
		val g2 = PetCareParser.groups(PetCareParser.parse(d.title, mod, PetCareParser.TRAINING, 54)!!).associate { it.name to it.tiles }
		assertTrue(g2.getValue(PetCareParser.SLOTS).first { it.label == "Training Slot #9" }.active)
		assertTrue(g2.getValue(PetCareParser.INFO).any { it.label == "Renamed Info" })
	}

	@Test fun titles() {
		MenuRegistry.clearForTests(); listOf(PetSitterMenu, OfferPetsMenu, PetTrainingMenu, MinionChestMenu).forEach(MenuRegistry::register)
		assertEquals("pet-sitter", MenuRegistry.match("Pet Sitter")?.id)
		assertEquals("offer-pets", MenuRegistry.match("Offer Pets")?.id)
		assertEquals("pet-training", MenuRegistry.match("Pet Training")?.id)
		assertEquals("minion-chest", MenuRegistry.match("Minion Chest")?.id)
		assertNull(MenuRegistry.match("Pets"))
	}

	@Test fun minionChest() {
		val d = RealDumpFixtures.load("minion-chest")
		val m = MinionChestParser.parse(d.title, d.slots, 63)!!
		assertEquals(27, m.chestSlots); assertEquals(0, m.used)
		val m2 = MinionChestParser.parse(d.title, d.slots + SlotText(0, "Quartz") + SlotText(5, "Quartz") + SlotText(40, "Inventory Thing"), 63)!!
		assertEquals(2, m2.used); assertEquals(mapOf("Quartz" to 2), m2.names)
		assertNull(MinionChestParser.parse(d.title, d.slots, 40))
	}
}
