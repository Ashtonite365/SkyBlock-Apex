package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.BoosterCookieParser
import dev.ashtonite365.apex.features.menus.h6.CalendarMenuParser
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.StatsParser
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class H6MenusTest {

	private fun both(name: String, parse: (String, List<SlotText>) -> HubModel?): HubModel {
		val d = RealDumpFixtures.load(name)
		val full = parse(d.title, d.slots)
		val live = parse(d.title, d.slots.filter { it.cleanName.isNotEmpty() })
		assertNotNull(full, name); assertEquals(full, live, "$name: snapshot vs full")
		return full!!
	}
	private fun HubModel.tile(name: String) = allTiles.first { HubText.clean(listOf(it.name)).first() == name }
	private fun HubModel.chip(l: String) = chips.first { it.first == l }.second

	@Test fun calendarEventsFromLore() {
		val m = both("calendar-events", CalendarMenuParser::parse)
		val ev = m.sections.first { it.title == "Upcoming events" }.tiles
		assertEquals(14, ev.size)
		assertEquals(listOf("in 17h 13m 20s", "lasts 1h"), m.tile("520th Spooky Festival").info)
		assertEquals(listOf("in 3d 16h 53m 20s"), m.tile("433rd Election Over!").info)
		assertEquals("520th Spooky Festival · 17h 13m 20s", m.chip("Next"))
		assertEquals("Diaz", m.chip("Mayor"))
		assertTrue(m.sections.first { it.title == "More" }.tiles.map { it.name }.containsAll(listOf("Event Rewards", "Calendar", "Chocolate Factory")))
		assertEquals(listOf("Back (SkyBlock Menu)" to 39, "Close" to 40), m.nav)
		assertEquals(62000.0, CalendarMenuParser.events(RealDumpFixtures.load("calendar-events").slots).first().seconds)
	}

	@Test fun calendarRejectsOtherTitles() = assertNull(CalendarMenuParser.parse("Calendar", RealDumpFixtures.load("calendar-events").slots))

	@Test fun bagsHub() {
		val m = both("your-bags", BagsParser::parse)

		assertEquals(listOf("Sack of Sacks", "Potion Bag", "Quiver", "Accessory Bag", "Time Pocket"), m.allTiles.map { HubText.clean(listOf(it.name)).first().removePrefix("✖ ") })
		val tp = m.allTiles.single { it.locked }
		assertEquals(24, tp.slot); assertTrue(tp.name.startsWith("§7✖ "))
		assertEquals("§cRequires Timite Rift Collection II.", tp.info.single())
		assertTrue(tp.tooltip.containsAll(listOf("A bag which holds items that evolve", "Requires Timite Rift Collection II.")))
		assertEquals("1", m.chip("Locked"))
		assertEquals(4, m.allTiles.count { !it.locked })
		assertEquals(listOf(0), m.allTiles.map { it.button }.distinct())
		assertFalse(m.allTiles.any { it.delayed })
		assertEquals("257", m.chip("Accessory Power"))
		assertEquals("Itchy", m.chip("Selected Power"))
	}

	@Test fun accessoryBagPage() {
		val m = both("accessory-bag", BagsParser::parse)
		assertEquals("1/2", m.chip("Page"))
		assertEquals(39, m.allTiles.size)
		assertTrue(m.nav.contains("Need more room?" to 50))
		val ring = m.tile("Titanium Ring")
		assertTrue(ring.name.startsWith("§9"))
		assertEquals(listOf("Mining Speed: +30", "Accessory Power: +8"), ring.info)
		assertTrue(m.chip("Accessory Power (page)").startsWith("+"))
		assertTrue(m.nav.any { it.first == "Next Page" })
	}

	@Test fun quiverAndSacks() {
		val q = both("quiver", BagsParser::parse)
		assertEquals(36, q.allTiles.size)
		assertEquals("34 slots", q.chip("Icy Arrow"))
		assertTrue(q.nav.contains("Clear Quiver" to 41))
		assertEquals(0, q.tile("Icy Arrow").button)
		val s = both("sack-of-sacks", BagsParser::parse)
		assertEquals(16, s.allTiles.size)
		assertEquals(1, s.tile("Large Combat Sack").button, "sacks: lore says Right Click to open")
		assertTrue(s.tile("Large Combat Sack").info.first().startsWith("Capacity: 20,160"))
		assertTrue(s.nav.contains("Insert inventory" to 41))
	}

	@Test fun bagTitles() {
		listOf("Your Bags", "Accessory Bag", "Accessory Bag (2/2)", "Quiver", "Potion Bag", "Sack of Sacks").forEach { assertTrue(BagsParser.TITLE.matches(it), it) }
		assertFalse(BagsParser.TITLE.matches("Sack Search"))

		assertNull(BagsParser.parse("Some Sack", listOf(SlotText(10, "Thing", listOf("No stored line")), SlotText(49, "Close", emptyList()))))
	}

	@Test fun boosterCookie() {
		val m = both("booster-cookie", BoosterCookieParser::parse)
		assertEquals("13,227", m.chip("Bits Purse"))
		assertEquals("4,980/43,507", m.chip("Bits Available"))
		assertEquals("1.133x", m.chip("Multiplier"))
		assertEquals("Settler · 26,900 Fame", m.chip("Fame"))
		assertEquals("75 Gems", m.chip("Gems"))
		assertTrue(m.tile("Booster Cookie").info.first().contains("Bits"))

		assertFalse(m.allTiles.any { it.name == "The Hex" }); assertEquals("1", m.chip("Locked"))
		val quick = m.sections.first { it.title == "Quick access" }.tiles.map { it.name }
		assertTrue(quick.containsAll(listOf("Anvil", "Auction House", "Bazaar", "Toggle Potion Effects")))
		assertFalse(m.allTiles.any { it.name == "Sell Item" })
		assertEquals(listOf("Back (SkyBlock Menu)" to 48), m.nav)
	}

	@Test fun statLine() {
		assertEquals("Health" to "2,116.5", StatsParser.stat("  Health 2,116.5"))
		assertEquals("Crit Chance" to "78.5%", StatsParser.stat("  Crit Chance 78.5%"))
		assertEquals("Charm Chance" to "0.04%", StatsParser.stat(" ❣ Charm Chance 0.04%"))
		assertNull(StatsParser.stat("Click for details!"))
	}

	@Test fun statsOverview() {
		val m = both("stats-equipment", StatsParser::parse)
		val cats = m.sections.first { it.title == "Stats" }.tiles.map { it.name }
		assertEquals(listOf("Combat", "Mining", "Farming", "Foraging", "Fishing", "Miscellaneous", "Hunting", "Wisdom"), cats)
		assertEquals("2,116.5", m.chip("Health"))
		assertEquals("105", m.chip("Vitality"))
		assertEquals("138/336 (41%)", m.chip("Achievements"))
		assertEquals("34", m.chip("Effects"))
		assertEquals(listOf("[Lvl 100] Griffin"), m.sections.first { it.title == "Pet" }.tiles.map { HubText.clean(listOf(it.name)).first() })
		assertEquals(8, m.sections.first { it.title == "Equipment" }.tiles.size)
		assertFalse(m.allTiles.any { it.name.contains("SkyBlock Menu") })
	}

	@Test fun statsBreakdowns() {
		val c = both("stats-breakdown-combat", StatsParser::parse)
		assertEquals("Combat Stats", c.chip("Category"))
		val def = c.allTiles.first { it.name.startsWith("Defense") }
		assertEquals(listOf("Damage Reduction: 90.3%", "Effective Health: 21,853❤"), def.info)
		assertEquals("Nope", c.chip("Show All Stats"))
		assertTrue(c.nav.any { it.first == "Back (Stats & Equipment)" })
		for (n in listOf("farming", "miscellaneous", "wisdom")) assertTrue(both("stats-breakdown-$n", StatsParser::parse).allTiles.isNotEmpty(), n)
	}
}
