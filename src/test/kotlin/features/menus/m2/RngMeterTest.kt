package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.rng.RngMeterMenu
import dev.ashtonite365.apex.features.menus.rng.RngMeterParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class RngMeterTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun nucleus() {
		val d = RealDumpFixtures.load("rng-nucleus")
		MenuRegistry.clearForTests(); MenuRegistry.register(RngMeterMenu)
		assertNull(MenuRegistry.match(d.title))
		assertEquals("rng-meter", MenuRegistry.match(d.title, d.slots)?.id)
		val m = RngMeterParser.parse(d.title, d.slots)!!
		assertEquals(17, m.drops.size)
		assertEquals(listOf("Rare", "Extraordinary", "Pray RNGesus", "RNGesus Incarnate"), RngMeterParser.groups(m).map { it.name }.filter { it != "Menu" })
		val p = m.drops.first { it.name == "Pickonimbus 2000" }
		assertEquals(3.2551, p.chancePct); assertEquals(0L, p.xp); assertEquals(4000L, p.xpNeeded); assertEquals("Nucleus XP", p.xpLabel)
		assertEquals(5_000_000L, m.drops.first { it.name == "Jade Dye" }.xpNeeded)
		assertNull(m.selected)
		assertEquals("Stored Nucleus XP: 0", m.stored)
		assertEquals(setOf("Search", "Reset RNG Drop"), m.buttons.map { it.cleanName }.toSet())
		assertEquals(48, m.nav.back); assertEquals(49, m.nav.close)
	}

	@Test fun editedSlayerMeter() {
		val d = RealDumpFixtures.load("rng-nucleus")
		val slots = d.slots.map { s ->
			s.copy(name = s.name.replace("Crystal Nucleus RNG Meter", "Revenant Horror RNG Meter"),
				lore = s.lore.map { it.replace("Nucleus XP", "Slayer XP") })
		}.map { if (it.slot == 10) it.copy(name = "Scythe Blade", lore = listOf("Odds: Insane (0.05%)", "", "Slayer XP: 1,500/3k", "", "SELECTED", "Click to deselect!")) else it } +
			SlotText(31, "Warden Heart", listOf("Odds: Brand New Tier (0.001%)", "", "Slayer XP: 0/9m"))
		val title = "Revenant Horror RNG Meter"
		MenuRegistry.clearForTests(); MenuRegistry.register(RngMeterMenu)
		assertEquals("rng-meter", MenuRegistry.match(title, slots)?.id)
		val m = RngMeterParser.parse(title, slots)!!
		assertEquals("Scythe Blade", m.selected?.name); assertEquals(0.5, m.selected?.progress)
		assertEquals("Stored Slayer XP: 0", m.stored)
		assertEquals(9_000_000L, m.drops.first { it.name == "Warden Heart" }.xpNeeded)
		val g = RngMeterParser.groups(m)
		assertTrue(g.any { it.name == "Insane" && it.tiles.single().active })
		assertTrue(g.any { it.name == "Brand New Tier" })

		val dungeon = (0 until 3).map { SlotText(40 + it, "Drop $it", listOf("Odds: Legendary", "Catacombs XP: 10/100")) }
		assertEquals(3, RngMeterParser.parse("Catacombs (F7) RNG Meter", dungeon)!!.drops.size)
	}

	@Test fun noFalseMatches() {

		assertFalse(RngMeterParser.isRngMeter(listOf(SlotText(10, "A", listOf("Odds: Rare (1%)", "Nucleus XP: 0/4,000")))))
		assertFalse(RngMeterParser.isRngMeter((10..14).map { SlotText(it, "A$it", listOf("Odds: Rare (1%)")) }))
		assertFalse(RngMeterParser.isRngMeter((10..14).map { SlotText(it, "A$it", listOf("Combat XP: 1/2")) }))
		assertFalse(RngMeterParser.isRngMeter((60..64).map { SlotText(it, "A$it", listOf("Odds: Rare", "X XP: 1/2")) }))
		for (f in listOf("hotm", "hotf-low", "pets", "quest-log", "chapters", "wardrobe-p1", "loadouts-p1", "pet-training"))
			assertFalse(RngMeterParser.isRngMeter(RealDumpFixtures.load(f).slots), f)
	}
}
