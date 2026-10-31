package dev.ashtonite365.apex.test.features.menus.skills

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skills.DungeoneeringParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class DungeoneeringMenuTest {
	@Test fun parses() {
		val d = RealDumpFixtures.load("skills-dungeoneering")
		val m = DungeoneeringParser.parse(d.title, d.slots)!!
		assertEquals(listOf("The Catacombs" to 24), m.dungeons.map { it.name to it.level })
		assertEquals(140_975.8, m.dungeons[0].progress!!.current)
		assertEquals(listOf("Healer", "Mage", "Berserk", "Archer", "Tank"), m.classes.map { it.name })
		assertEquals(22, m.classes.first { it.name == "Archer" }.level)
		assertEquals(listOf("Your Essence" to 50), m.extras)
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
		assertNull(DungeoneeringParser.parse("Your Skills", d.slots))
		assertNull(DungeoneeringParser.parse(d.title, d.slots.filter { it.slot < 12 || it.slot > 40 }))
	}
}
