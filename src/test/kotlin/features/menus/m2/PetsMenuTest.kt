package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.pets.PetGrouping
import dev.ashtonite365.apex.features.menus.pets.PetsMenu
import dev.ashtonite365.apex.features.menus.pets.PetsParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PetsMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun parsesLivePets() {
		val d = RealDumpFixtures.load("pets")
		val m = PetsParser.parse(d.title, d.slots)!!
		assertEquals(13, m.pets.size)
		val griffin = m.active!!
		assertEquals("Griffin", griffin.name); assertEquals(100, griffin.level); assertEquals("Spooky Cupcake", griffin.heldItem)
		assertTrue(griffin.maxLevel); assertEquals("29,146,754 XP", griffin.xp); assertEquals("Combat", griffin.type)
		val rabbit = m.pets.first { it.name == "Rabbit" }
		assertEquals(41, rabbit.level); assertEquals(55.1, rabbit.progressPct); assertEquals("13,873.2/25.2k", rabbit.xp); assertNull(rabbit.heldItem); assertFalse(rabbit.active)
		assertEquals("Mining", m.pets.first { it.name == "Armadillo" }.type)
		assertEquals(1, m.pets.count { it.active })
		assertTrue(m.buttons.any { it.cleanName == "Sort" }); assertTrue(m.buttons.none { it.cleanName == "Close" })
		assertEquals(49, m.nav.close); assertEquals(48, m.nav.back)
	}

	@Test fun grouping() {
		val d = RealDumpFixtures.load("pets")
		val pets = PetsParser.parse(d.title, d.slots)!!.pets
		val byType = PetsParser.group(pets, PetGrouping.TYPE).toMap()
		assertEquals(setOf("Combat", "Farming", "Fishing", "Enchanting", "Mining"), byType.keys)
		assertEquals(pets.size, byType.values.sumOf { it.size })
		assertEquals("Legendary", PetsParser.rarity("§7[Lvl 100] §6Griffin"))
		assertEquals(listOf("Legendary", "Rare"), PetsParser.group(pets.take(2).mapIndexed { i, p -> p.copy(rarity = if (i == 0) "Rare" else "Legendary") }, PetGrouping.RARITY).map { it.first })
	}

	@Test fun titleAndToggle() {
		M2.reg(PetsMenu, true)
		assertTrue(M2.attaches("Pets")); assertTrue(M2.attaches("Pets (2/3)"))
		assertNull(MenuRegistry.match("Pet Sitter")); assertNull(MenuRegistry.match("Offer Pets"))
		M2.reg(PetsMenu, false); assertTrue(M2.vanilla("Pets"))
		assertNull(PetsParser.parse("Pets", listOf(SlotText(0, ""))))
	}

	@Test fun fromWaysToLevelUp() {
		M2.reg(PetsMenu, true)
		val d = RealDumpFixtures.load("pets-from-leveling")
		assertEquals("pets", MenuRegistry.match(d.title, d.slots)?.id)
		val m = PetsParser.parse(d.title, d.slots)!!
		assertEquals(13, m.pets.size); assertEquals("Griffin", m.active!!.name); assertEquals("Griffin", m.selectedName)
		assertEquals("To Ways to Level Up", m.nav.backLabel); assertEquals(48, m.nav.back)
		assertEquals(1, m.nav.page); assertNull(m.nav.next)
		assertTrue(m.buttons.none { it.cleanName == "Pets" })
		assertEquals(listOf("§e▶ Rarity"), PetsParser.buttonTile(m.buttons.first { it.cleanName == "Sort" }).info)
		assertEquals(listOf("§e▶ Hide all pets"), PetsParser.buttonTile(m.buttons.first { it.cleanName == "Pet visibility" }).info)
	}

	@Test fun singlePage() {
		M2.reg(PetsMenu, true)
		val d = RealDumpFixtures.load("pets-single")
		assertEquals("pets", MenuRegistry.match(d.title, d.slots)?.id)
		val m = PetsParser.parse(d.title, d.slots)!!
		assertTrue(m.pets.isNotEmpty()); assertNotNull(m.active ?: m.selectedName?.let { it })
		assertEquals(1 to 1, m.nav.page to m.nav.pages); assertNull(m.nav.prev); assertNull(m.nav.next)
		assertEquals("", TileParse.pageLabel(m.nav)); assertEquals("To SkyBlock Menu", m.nav.backLabel)
		assertTrue(m.buttons.none { it.slot == 45 || it.slot == 53 })

		assertNull(TileParse.pageFromArrows(null, null))
		assertEquals(" §7(1/2)", TileParse.pageLabel(TileParse.nav(listOf(SlotText(53, "Next Page", listOf("(1/2)"))), 54)))
	}

	@Test fun bottomBarIcons() {
		for (fx in listOf("pets-single", "pets-from-leveling")) {
			val d = RealDumpFixtures.load(fx)
			val m = PetsParser.parse(d.title, d.slots)!!
			val sections = PetsParser.sections(m, PetGrouping.TYPE)
			assertTrue(sections.none { it.name == "Menu" }, fx)
			assertEquals(m.pets.map { it.slot }.toSet(), sections.flatMap { it.tiles }.map { it.slot }.toSet(), fx)
			val bar = PetsParser.bottomBar(m)
			assertEquals(listOf(7 to "Exp Sharing", 8 to "+28 Pet Slots", 47 to "Pet Score Rewards", 50 to "Search", 51 to "Pet visibility", 52 to "Sort"),
				bar.map { it.slot to it.name }, fx)
			assertEquals(bar.size, bar.map { it.slot }.toSet().size)
			bar.forEach { ic -> assertEquals(d.slots.first { it.slot == ic.slot }.cleanLore, ic.lore) }
			assertTrue(bar.none { it.slot in setOf(48, 49, 4) })
		}

		val d = RealDumpFixtures.load("pets-single")
		val edited = d.slots.filter { it.slot != 8 } + SlotText(46, "Pet Skins", listOf("Browse skins"))
		assertEquals(listOf(7, 46, 47, 50, 51, 52), PetsParser.bottomBar(PetsParser.parse(d.title, edited)!!).map { it.slot })
	}
}
