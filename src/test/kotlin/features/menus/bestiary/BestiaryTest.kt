// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.bestiary

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.bestiary.*
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class BestiaryTest {
	private fun load(n: String) = RealDumpFixtures.load(n).let { BestiaryParser.parse(it.title, it.slots) }
	private fun area(n: String) = load(n) as BestiaryPage.Area
	private fun ms(n: String) = load(n) as BestiaryPage.Milestones

	@Test fun main() {
		val p = load("bestiary-main") as BestiaryPage.Main
		assertEquals(21, p.areas.size)
		assertEquals("Your Island", p.areas.first().name); assertEquals("Lotus Atoll", p.areas.last().name)
		assertEquals(123.0, p.found!!.current); assertEquals(359.0, p.found!!.max); assertEquals(10.0, p.completed!!.current)
		val hub = p.areas.first { it.name == "Hub" }
		assertEquals(6.0 to 7.0, hub.found!!.current to hub.found!!.max); assertEquals(2.0, hub.completed!!.current)
		assertEquals(91, p.milestone!!.level); assertEquals(92, p.milestone!!.nextLevel); assertEquals(916.0, p.milestone!!.totalTiers)
		assertEquals(6.0 to 10.0, p.milestone!!.next!!.current to p.milestone!!.next!!.max)
		assertEquals(52, p.toggle!!.slot); assertEquals(true, p.toggle!!.on)
		assertTrue(p.nav.map { it.name }.containsAll(listOf("Go Back", "Close", "Search Bestiary")))
	}

	@Test fun hubFamilies() {
		val p = area("bestiary-hub")
		assertEquals("Hub", p.area); assertNull(p.page)
		assertEquals(7, p.families.size); assertEquals(6, p.unlocked); assertEquals(2, p.maxed)
		val ghoul = p.families.first { it.base == "Crypt Ghoul" }
		assertEquals(12, ghoul.tier); assertEquals(13, ghoul.nextTier); assertEquals(4291.0, ghoul.kills); assertEquals(8.0, ghoul.deaths)
		assertEquals(1291.0, ghoul.next!!.current); assertEquals(2000.0, ghoul.next!!.max); assertFalse(ghoul.maxed)
		assertTrue(ghoul.bonuses.first().contains("Strength")); assertEquals(listOf("Undead"), ghoul.mobTypes)
		val zombie = p.families.first { it.base == "Graveyard Zombie" }
		assertTrue(zombie.maxed); assertEquals(5, zombie.tier); assertNull(zombie.next); assertEquals(200.0, zombie.overall!!.max)
		val pig = p.families.first { it.name == "Shiny Pig" }
		assertTrue(pig.locked); assertEquals(0, pig.tier)
	}

	@Test fun farmingCapsAndTierZero() {
		val p = area("bestiary-farming-islands")
		val cow = p.families.first { it.base == "Mushroom Cow" }
		assertEquals(0, cow.tier); assertEquals(1, cow.nextTier); assertEquals(5, cow.cap); assertFalse(cow.locked)
		assertTrue(cow.bonuses.isNotEmpty())
		assertEquals(mapOf("Chicken" to 3, "Rabbit" to 1, "Sheep" to 2), listOf("Chicken", "Rabbit", "Sheep").associateWith { b -> p.families.first { it.base == b }.tier })
	}

	@Test fun gardenAllLocked() {
		val p = area("bestiary-garden")
		assertTrue(p.families.isNotEmpty()); assertEquals(0, p.unlocked); assertTrue(p.families.all { it.locked })
		assertEquals(17.0, p.found!!.max)
	}

	@Test fun paging() {
		val p1 = area("bestiary-moonglade-1"); val p2 = area("bestiary-moonglade-2")
		assertEquals("Moonglade Marsh", p1.area); assertEquals(1 to 2, p1.page); assertEquals(2 to 2, p2.page)
		assertNotNull(p1.nav.firstOrNull { it.name == "Next Page" }); assertEquals(45, p2.nav.first { it.name == "Previous Page" }.slot)
		assertEquals(5, p2.families.size)
		val chill = p1.families.first { it.base == "Chill" }; assertEquals(2, chill.tier); assertEquals(3, chill.nextTier)

		assertEquals(2 to 2, BestiaryParser.paging(p2.nav)); assertEquals(1 to 0, BestiaryParser.paging(p1.nav))
	}

	@Test fun cutTitle() {
		val p = area("bestiary-mythological")
		assertEquals("Mythological Creatures", p.area)
		assertEquals(3, p.unlocked); assertEquals(12.0, p.found!!.max)
		assertEquals("Mythological", p.families.first { it.base == "Gaia Construct" }.mobTypes.first())
	}

	@Test fun subAreaHubs() {
		val safari = area("bestiary-critter-safari")
		assertTrue(safari.families.isEmpty())
		assertEquals(listOf("Cavern Biome", "Forest Biome", "Haunted Biome", "Icy Biome"), safari.subAreas.map { it.name })
		val fishing = area("bestiary-fishing")
		assertEquals("Fishing", fishing.area); assertTrue(fishing.subAreas.size >= 6)
		assertEquals(7.0 to 24.0, fishing.subAreas.first().found!!.let { it.current to it.max })
	}

	@Test fun subAreaPagePredicate() {
		val fams = RealDumpFixtures.load("bestiary-mythological").slots
		assertTrue(BestiaryParser.isSubAreaPage("Critter Safari ➜ Mythological Creatures", fams))
		assertFalse(BestiaryParser.isSubAreaPage("Bestiary", fams))
		val hop = RealDumpFixtures.load("bestiary-milestones-1")
		assertFalse(BestiaryParser.isSubAreaPage("Fishing ➜ Trophy Fish", hop.slots))
	}

	@Test fun titles() {
		for (t in listOf("Bestiary ➜ Hub", "(1/2) Bestiary ➜ Moonglade Marsh", "(2/2) Bestiary ➜ The Catacombs", "Bestiary ➜ Mythological Creatur"))
			assertTrue(BestiaryParser.AREA.matches(t), t)
		for (t in listOf("Bestiary", "Bestiary Milestones", "Tasks ➜ Slaying", "Fishing ➜ Trophy Fish", "Collected Mob Types"))
			assertFalse(BestiaryParser.AREA.matches(t), t)
		assertTrue(BestiaryParser.MAIN.matches("Bestiary")); assertFalse(BestiaryParser.MAIN.matches("Bestiary Milestones"))
		assertTrue(BestiaryParser.MILESTONES.matches("Bestiary Milestones"))
	}

	@Test fun milestonesAroundCurrent() {
		val p = ms("bestiary-milestones-81")
		assertEquals((81..101).toList(), p.tiles.map { it.level })
		assertEquals(91, p.current)
		val cur = p.inProgress!!; assertEquals(92, cur.level); assertEquals(6.0 to 10.0, cur.progress)
		assertTrue(p.tiles.filter { it.level <= 91 }.all { it.state == BeastState.UNLOCKED })
		assertTrue(p.tiles.filter { it.level > 92 }.all { it.state == BeastState.LOCKED })
		assertEquals(listOf("+1 Health", "+1,000,000 Combat Experience", "+Beastslayer's Codex Upgrade", "+10 SkyBlock XP"), p.tiles.first { it.level == 100 }.rewards)
		assertEquals(45, p.scrollLeft); assertEquals(53, p.scrollRight)
		assertEquals(52, p.reminders!!.slot); assertEquals(true, p.reminders!!.on)
	}

	@Test fun milestonesEnds() {
		val first = ms("bestiary-milestones-1")
		assertEquals(1, first.tiles.first().level); assertNull(first.scrollLeft); assertEquals(53, first.scrollRight)
		assertTrue(first.tiles.all { it.state == BeastState.UNLOCKED })
		val last = ms("bestiary-milestones-492")
		assertEquals(514, last.tiles.last().level); assertNull(last.scrollRight); assertEquals(45, last.scrollLeft)
		assertTrue(last.tiles.all { it.state == BeastState.LOCKED }); assertNull(last.current)
		assertEquals(false, last.reminders!!.on); assertEquals(50, last.recenter)
	}

	@Test fun rememberAndRanges() {
		var seen = emptyMap<Int, BeastMilestone>()
		for (n in listOf("bestiary-milestones-1", "bestiary-milestones-81", "bestiary-milestones-87", "bestiary-milestones-492"))
			seen = BestiaryParser.remember(seen, ms(n).tiles)
		assertEquals(listOf(1..20, 81..110, 492..514), BestiaryParser.ranges(seen.keys))
		assertEquals(BeastState.IN_PROGRESS, seen[92]!!.state)
	}

	@Test fun romanGeneral() {
		assertEquals(495, BestiaryParser.milestone(dev.ashtonite365.apex.features.menus.support.SlotText(0, "Bestiary Milestone CDXCV", listOf("Reward:", "+1 Health")))!!.level)
	}
}

class BestiaryMemoryTest {
	@Test fun milestoneRoundTrip() {
		val m = BestiaryMilestoneData(mutableMapOf(92 to BeastMilestone(32, 92, BeastState.IN_PROGRESS, listOf("+1 Health"), 6.0 to 10.0)))
		val j = kotlinx.serialization.json.Json.encodeToString(BestiaryMilestoneData.serializer(), m)
		assertEquals(m, kotlinx.serialization.json.Json.decodeFromString(BestiaryMilestoneData.serializer(), j))
	}
}
