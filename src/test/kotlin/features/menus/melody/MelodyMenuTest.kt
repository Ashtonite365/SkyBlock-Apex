package dev.ashtonite365.apex.test.features.menus.melody

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.melody.MelodyParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class MelodyMenuTest {
	@Test fun parses() {
		val d = RealDumpFixtures.load("melody")
		val m = MelodyParser.parse(d.title.replace("\\u266B", "♫"), d.slots)!!
		assertEquals(listOf("Easy", "Hard", "Expert", "Virtuoso", "Prodigy"), m.byDifficulty.keys.toList())
		assertEquals(13, m.songs.size)
		val hymn = m.songs.first { it.slot == 10 }
		assertEquals(100.0, hymn.best); assertEquals(2, hymn.perfects); assertNull(hymn.locked)
		val camp = m.songs.first { it.name == "Through the Campfire" }
		assertEquals(45.7, camp.best); assertEquals(5, camp.rewards.size)
		val pach = m.songs.first { it.name == "Pachelbel" }
		assertNull(pach.best); assertEquals("Score 90% on previous song!", pach.locked)
		assertEquals(mapOf("Close" to 40), m.nav)
	}

	@Test fun listDeniedUntilH4() {

		org.junit.jupiter.api.Assertions.assertFalse(MenuRegistry.denied("Melody ♫"))
		org.junit.jupiter.api.Assertions.assertTrue(MenuRegistry.denied("Harp - Hymn to the Joy"))
	}

	@Test fun harpScreenNeverMatched() {
		for (t in listOf("Harp - Hymn to the Joy", "Harp - Through the Campfire", "Harp", "Melody's Harp")) {
			assertEquals(false, MelodyParser.TITLE.matches(t), t)
		}
		assertEquals(true, MenuRegistry.denied("Harp - Hymn to the Joy"))

		val harp = (0 until 54).map { dev.ashtonite365.apex.features.menus.support.SlotText(it, if (it % 9 < 7) "| Click!" else "", listOf("Note")) }
		assertNull(MelodyParser.parse("Melody ♫", harp))
		MenuRegistry.clearForTests(); MenuRegistry.register(dev.ashtonite365.apex.features.menus.melody.MelodyMenu)
		assertEquals(MenuRegistry.Resolution.None, MenuRegistry.resolve("Harp - Hymn to the Joy") { null })
		MenuRegistry.clearForTests()
	}
}
