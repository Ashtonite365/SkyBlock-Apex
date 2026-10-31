package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.minion.MinionChestParser
import dev.ashtonite365.apex.features.menus.pets.OfferPetsMenu
import dev.ashtonite365.apex.features.menus.pets.PetCareParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.wardrobe.SetState
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeMenu
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class M2bDumpTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun wardrobeTitles() {
		M2.reg(WardrobeMenu, true)
		for (p in 1..3) {
			val d = RealDumpFixtures.load("wardrobe-p$p")
			assertEquals("($p/3) Armor Sets", d.title)
			assertTrue(M2.attaches(d.title))

			assertTrue(WardrobeParser.KEYBIND_TITLE.matches(d.title))
		}
		assertTrue(M2.attaches("(1/2) Equipment Sets"))
		assertNull(MenuRegistry.match("Armor Sets Upgrade"))
		assertNull(MenuRegistry.match("Sets"))
	}

	@Test fun wardrobePage1() {
		val d = RealDumpFixtures.load("wardrobe-p1")
		val m = WardrobeParser.parse(d.title, d.slots)!!
		assertEquals(9, m.sets.size)
		assertEquals(listOf(SetState.READY, SetState.READY, SetState.EQUIPPED, SetState.EMPTY) + List(5) { SetState.LOCKED }, m.sets.map { it.state })
		assertEquals((1..9).toList(), m.sets.map { it.number })
		assertEquals(4, m.sets[0].pieces.size)
		assertTrue(m.sets[0].pieces[0].startsWith("Ancient Tarantula Helmet"))
		assertTrue(m.sets[3].pieces.isEmpty())
		assertTrue(m.sets[4].pieces.isEmpty())
		assertNull(m.nav.prev); assertNotNull(m.nav.next)
		assertEquals(1 to 3, m.nav.page to m.nav.pages)

		assertEquals(36, WardrobeParser.keySlot(m, 0, false)); assertNull(WardrobeParser.keySlot(m, 2, false)); assertEquals(38, WardrobeParser.keySlot(m, 2, true))
		assertNull(WardrobeParser.keySlot(m, 3, true)); assertNull(WardrobeParser.keySlot(m, 4, true))
	}

	@Test fun wardrobeLaterPages() {
		val p2 = RealDumpFixtures.load("wardrobe-p2").let { WardrobeParser.parse(it.title, it.slots)!! }
		assertEquals((10..18).toList(), p2.sets.map { it.number })
		assertNotNull(p2.nav.prev); assertNotNull(p2.nav.next)
		val p3 = RealDumpFixtures.load("wardrobe-p3").let { WardrobeParser.parse(it.title, it.slots)!! }
		assertEquals((19..27).toList(), p3.sets.map { it.number })
		assertTrue(p3.sets.all { it.state == SetState.LOCKED && it.pieces.isEmpty() })
		assertEquals(45, p3.nav.prev); assertNull(p3.nav.next)
	}

	@Test fun minionChestWithItems() {
		val d = RealDumpFixtures.load("minion-chest-items")
		val m = MinionChestParser.parse(d.title, d.slots, 63)!!
		assertEquals(27, m.chestSlots); assertEquals(1, m.used)
		assertEquals(mapOf("Heroic Rogue Sword" to 1), m.names)
		val after = RealDumpFixtures.load("minion-chest-items-2")
		assertEquals(0, MinionChestParser.parse(after.title, after.slots, 63)!!.used)
	}

	@Test fun petTrainingFannDump() {
		val d = RealDumpFixtures.load("pet-training-fann")
		val g = PetCareParser.groups(PetCareParser.parse(d.title, d.slots, PetCareParser.TRAINING, 54)!!).associate { it.name to it.tiles }
		assertEquals(listOf("Training Slot #1"), g.getValue(PetCareParser.SLOTS).map { it.label })
		assertEquals(2, g.getValue(PetCareParser.LOCKED).size)
		assertEquals(1, g.getValue(PetCareParser.PREVIEW).size); assertNull(g[PetCareParser.PETS])
		assertTrue(g.getValue(PetCareParser.INFO).any { it.label == "Extra Free Training" })
		assertNull(PetCareParser.petSlot(PetCareParser.parse(d.title, d.slots, PetCareParser.TRAINING, 54)!!))
	}

	@Test fun offerPets() {
		val d = RealDumpFixtures.load("offer-pets")
		assertEquals("Offer Pets", d.title)
		val p = PetCareParser.parse(d.title, d.slots, PetCareParser.OFFER, PetCareParser.chestSize(81))!!
		assertEquals(13, PetCareParser.petSlot(p))
		val g = PetCareParser.groups(p)
		assertEquals("Place pet", g.first().name); assertEquals(13, g.first().tiles.single().slot)
		assertTrue(g.flatMap { it.tiles }.any { it.label == "+1 Taming Level Cap" })
		assertTrue(g.flatMap { it.tiles }.none { it.label == "Offer a Pet" })

		val mod = d.slots.filter { it.slot != 13 } + SlotText(13, "[Lvl 5] Some Pet", listOf("Combat Pet")) +
			SlotText(42, "Brand New Button", listOf("Does things"))
		val g2 = PetCareParser.groups(PetCareParser.parse(d.title, mod, PetCareParser.OFFER, 45)!!)
		assertEquals("Some Pet", g2.first().tiles.single().label.removePrefix("[Lvl 5] "))
		assertTrue(g2.first().tiles.single().active)
		assertTrue(g2.flatMap { it.tiles }.any { it.label == "Brand New Button" })
		M2.reg(OfferPetsMenu, true); assertTrue(M2.attaches("Offer Pets"))
	}

	@Test fun petSitterSlotStillFound() {
		val d = RealDumpFixtures.load("pet-sitter")
		val p = PetCareParser.parse(d.title, d.slots, PetCareParser.SITTER, 45)!!
		assertEquals(13, PetCareParser.petSlot(p))
	}
}
