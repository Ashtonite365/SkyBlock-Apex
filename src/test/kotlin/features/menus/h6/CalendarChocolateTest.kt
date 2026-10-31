package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.BoosterCookieParser
import dev.ashtonite365.apex.features.menus.h6.CalendarMenuParser
import dev.ashtonite365.apex.features.menus.h6.ChocolateParser
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CalendarChocolateTest {
	private fun parseWith(n: String, p: (String, List<dev.ashtonite365.apex.features.menus.support.SlotText>) -> HubModel?) =
		RealDumpFixtures.load(n).let { p(it.title, it.slots.filter { s -> s.cleanName.isNotEmpty() }) }
	private fun cal(n: String) = parseWith(n, CalendarMenuParser::parse)!!
	private fun choc(n: String) = parseWith(n) { t, s -> ChocolateParser.parse(t, s) }!!
	private fun HubModel.chip(l: String) = chips.firstOrNull { it.first == l }?.second
	private fun HubModel.named(n: String) = allTiles.first { HubText.clean(listOf(it.name)).first() == n }

	@Test fun calendarEventsAreTextOnly() {
		val m = cal("cal-calendar-events")
		val ev = m.sections.first { it.title == "Upcoming events" }.tiles
		assertTrue(ev.isNotEmpty()); assertTrue(ev.none { it.clickable })
		assertTrue(m.sections.first { it.title == "More" }.tiles.all { it.clickable })
		assertTrue(m.named("Event Rewards").clickable)
	}

	@Test fun eventRewardsLiveEmpty() {
		val m = cal("cal-event-rewards")
		assertEquals("Event Rewards", m.heading)
		assertTrue(m.allTiles.isEmpty())
		assertEquals("You currently don't have any event rewards! Check back later!", m.emptyText)
		assertEquals("0", m.chip("Rewards"))
		assertEquals("Rewards expire after 10 SkyBlock years!", m.chip("WARNING"))
	}

	@Test fun monthPagesFromTitleAndLore() {
		val a = cal("cal-month-autumn")
		assertEquals("Autumn", a.chip("Month")); assertEquals("520", a.chip("Year")); assertEquals("8", a.chip("Page"))
		assertTrue(a.nav.contains("Prev (Page 7)" to 45)); assertTrue(a.nav.contains("Next (Page 9)" to 53))
		val d1 = a.named("Day 1"); assertEquals(listOf("Jacob's Farming Contest"), d1.info); assertFalse(d1.clickable)
		assertEquals(listOf("No events"), a.named("Day 2").info)
		for (n in listOf("cal-month-early-autumn", "cal-month-late-summer")) {
			val m = cal(n); assertTrue(m.allTiles.size >= 28, n); assertNotNull(m.chip("Month")); assertEquals("520", m.chip("Year"))
		}
		assertEquals("Late Summer", cal("cal-month-late-summer").chip("Month"))
		assertTrue(CalendarMenuParser.TITLE.matches("Early Spring, Year 521"))
		assertFalse(CalendarMenuParser.TITLE.matches("Calendar"))
	}

	@Test fun chocolateFactoryFromLore() {
		val m = choc("choc-factory")
		assertEquals("100,000,000,000", m.chip("Chocolate"))
		assertEquals("13,980.08 per second", m.chip("Production"))
		assertEquals("297,452,292,874", m.chip("All-time"))
		assertEquals("64/452 Rabbits", m.chip("Barn"))

		assertEquals(listOf("Chocolate", "Employees", "Upgrades", "Menus"), m.sections.map { it.title })
		assertEquals(listOf(13), m.sections[0].tiles.map { it.slot })
		assertTrue(m.sections[0].tiles.single().clickable, "the chocolate item forwards the player's one click")
		val row = m.sections.first { it.title == "Employees" }.tiles
		assertEquals((27..35).toList(), row.map { it.slot })
		val emp = row.filter { it.slot in 28..34 }
		assertEquals(7, emp.size); assertEquals("Rabbit Bro", emp.first().name); assertEquals("[112] Assistant", emp.first().info.first())
		assertEquals(listOf(37, 38, 39, 41, 42, 43), m.sections.first { it.title == "Upgrades" }.tiles.map { it.slot })
		assertEquals(listOf(45, 47, 50, 51, 52, 53), m.sections.first { it.title == "Menus" }.tiles.map { it.slot })
		for (sec in m.sections) assertEquals(sec.tiles.map { it.slot }.sorted(), sec.tiles.map { it.slot })
		assertTrue(m.sections.first { it.title == "Upgrades" }.tiles.any { it.name == "Time Tower V" })
		val best = ChocolateParser.parse(RealDumpFixtures.load("choc-factory").title, RealDumpFixtures.load("choc-factory").slots, best = 30)!!
		assertEquals(listOf(30), best.allTiles.filter { it.highlight }.map { it.slot })
	}

	@Test fun chocolateShopCostsAndStock() {
		val m = choc("choc-shop")
		val dye = m.named("Chocolate Dye")
		assertEquals("§640,000,000,000 Chocolate", dye.info[0]); assertEquals("§71 left", dye.info[1])
		assertFalse(m.allTiles.any { it.name == "Sell Item" })
		assertEquals("58,646,000,000", m.chip("Chocolate spent"))
	}

	@Test fun hoppityPagesLiveStatus() {
		val p1 = choc("choc-hoppity-1")
		assertEquals("1/21", p1.chip("Page"))
		assertTrue(p1.allTiles.isNotEmpty())
		val notFound = p1.named("Aaron"); assertEquals("§7Not found", notFound.info.first())
		assertTrue(p1.nav.contains("Sort: A to Z" to 50)); assertTrue(p1.nav.contains("Filter: None" to 51))
		val p4 = choc("choc-hoppity-4")
		assertEquals("4/21", p4.chip("Page"))
		assertEquals("11.1% (64/577)", p4.chip("Found"))
		val (found, all) = p4.chip("On page")!!.split('/').map { it.toInt() }
		assertEquals(RealDumpFixtures.load("choc-hoppity-4").slots.count { HubText.rarity(it.lore) != null && !HubText.clean(it.lore).contains(ChocolateParser.NOT_FOUND) && it.cleanName != "Hoppity's Collection" }, found)
		assertTrue(all > 0)
		assertEquals("21/21", choc("choc-hoppity-21").chip("Page"))
	}

	@Test fun hitmanLockedSlotsNotOffered() {
		val m = choc("choc-hitman")
		assertEquals("0", m.chip("Available eggs")); assertEquals("0/28", m.chip("Purchased slots"))
		assertEquals(1, m.allTiles.size, "only the purchasable slot; 'Requires previous unlock!' slots are counted")
		assertTrue(m.chip("Locked")!!.toInt() > 0)
		assertTrue(m.nav.any { it.first == "Claim All" })
	}

	@Test fun milestonesStatus() {
		val m = choc("choc-milestones")
		val u = m.sections.first { it.title == "Unlocked" }.tiles
		assertEquals(16, u.size)
		val l = m.sections.first { it.title == "Locked" }.tiles
		assertTrue(l.any { it.info.first() == "Locked · 49%" })
		assertTrue(m.allTiles.none { it.clickable })
		assertEquals("16/${m.allTiles.size}", m.chip("Unlocked (shown)"))
		assertTrue(m.nav.any { it.first == "Scroll Left" })
	}

	@Test fun boosterCookieRealDump1103() {
		val m = parseWith("cookie-1103", BoosterCookieParser::parse)!!
		assertEquals("14,076", m.chip("Bits Purse")); assertEquals("4,131/43,507", m.chip("Bits Available"))
		assertEquals("0d 1h 17m 53s", m.chip("Cookie left")); assertEquals("+5,438 bits available", m.chip("Per cookie"))
		assertEquals("Citizen (26,900/80k)", m.chip("Next rank")); assertEquals("75 Gems", m.chip("Gems"))
		val buffs = m.named("Booster Cookie").info
		assertTrue(buffs.any { it.startsWith("Quick access to some menus using their respective commands:") }, buffs.toString())
		assertTrue(buffs.none { it.startsWith("/") })
		assertFalse(m.allTiles.any { it.name == "The Hex" }); assertEquals("1", m.chip("Locked"))
	}
}
