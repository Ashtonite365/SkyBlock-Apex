package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.heart.HeartParser
import dev.ashtonite365.apex.features.menus.heart.HotfMenu
import dev.ashtonite365.apex.features.menus.heart.HotmMenu
import dev.ashtonite365.apex.features.menus.heart.PerkState
import dev.ashtonite365.apex.features.menus.heart.TierState
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class HeartMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun hotm(slots: List<SlotText>? = null) = RealDumpFixtures.load("hotm").let { HeartParser.parse(it.title, slots ?: it.slots, HotmMenu.title)!! }

	@Test fun hotmTree() {
		val m = hotm()
		assertEquals(listOf("Tier 5", "Tier 4", "Tier 3", "Tier 2", "Tier 1"), m.tiers.map { it.name })
		assertTrue(m.tiers.all { it.state == TierState.UNLOCKED })
		val p = m.tiers.flatMap { it.perks }.associateBy { it.name }
		assertEquals(16 to 140, p.getValue("Professional").level to p.getValue("Professional").maxLevel)
		assertEquals(PerkState.ENABLED, p.getValue("Professional").state); assertTrue(p.getValue("Professional").affordable)
		assertEquals("771 Gemstone Powder", p.getValue("Professional").cost)
		assertFalse(p.getValue("Mole").affordable)
		assertEquals(20 to null, p.getValue("Old-School").level to p.getValue("Old-School").maxLevel)
		assertEquals(PerkState.LOCKED, p.getValue("Seasoned Mineman").state); assertEquals("Quick Forge", p.getValue("Seasoned Mineman").requires)
		assertEquals(PerkState.ABILITY, p.getValue("Pickobulus").state)
		assertEquals(PerkState.UNLOCKABLE, p.getValue("Gem Lover").state)
		assertEquals(listOf("Token of the Mountain" to "0", "Mithril Powder" to "30,011", "Gemstone Powder" to "885", "Glacite Powder" to "0"), m.resources)
		assertEquals(8, m.nav.prev); assertNull(m.nav.next); assertEquals(45, m.nav.close); assertEquals(48, m.nav.back)
		assertTrue(m.buttons.any { it.cleanName == "Reset Heart of the Mountain" }); assertTrue(m.buttons.none { it.cleanName == "Heart of the Mountain" })
		val g = HeartParser.groups(m, true, highlighted = setOf("Mole"))
		assertTrue(g.first { it.name.startsWith("Tier 3") }.tiles.any { it.label.endsWith("Efficient Miner") })
		assertTrue(g.flatMap { it.tiles }.first { it.label.endsWith("Mole") }.active)
	}

	@Test fun hotmTop() {
		val m = RealDumpFixtures.load("hotm-top").let { HeartParser.parse(it.title, it.slots, HotmMenu.title)!! }
		assertEquals(listOf("Tier 10", "Tier 9", "Tier 8", "Tier 7", "Tier 6"), m.tiers.map { it.name })
		assertEquals(TierState.PROGRESS, m.tiers[3].state); assertEquals("45.6% 68,366/150k", m.tiers[3].progress)
		assertEquals(TierState.UNLOCKED, m.tiers[4].state)
		assertEquals(PerkState.LOCKED, m.tiers[0].perks.first { it.name == "Gemstone Infusion" }.state)
		assertEquals(PerkState.LOCKED, m.tiers[0].perks.first { it.name == "Crystalline" }.state)
		assertEquals(10 to 20, m.tiers[4].perks.first { it.name == "Blockhead" }.let { it.level to it.maxLevel })
		assertNull(m.nav.prev); assertEquals(53, m.nav.next)
	}

	@Test fun hotfPages() {
		val low = RealDumpFixtures.load("hotf-low").let { HeartParser.parse(it.title, it.slots, HotfMenu.title)!! }
		assertEquals(listOf("Tier 5", "Tier 4", "Tier 3", "Tier 2", "Tier 1"), low.tiers.map { it.name })
		assertEquals(TierState.PROGRESS, low.tiers[3].state); assertEquals("7% 210/3k", low.tiers[3].progress)
		assertEquals(TierState.UNLOCKED, low.tiers[4].state); assertEquals(TierState.LOCKED, low.tiers[0].state)
		val sweep = low.tiers[4].perks.single()
		assertEquals("Sweep", sweep.name); assertEquals(PerkState.UNLOCKABLE, sweep.state); assertTrue(sweep.affordable)
		assertEquals(listOf("Tokens of the Forest" to "1", "Forest Whispers" to "3,026", "Desert Whispers" to "0"), low.resources)
		assertEquals(8, low.nav.prev); assertNull(low.nav.next)
		val top = RealDumpFixtures.load("hotf-top").let { HeartParser.parse(it.title, it.slots, HotfMenu.title)!! }
		assertEquals("Tier 8", top.tiers[0].name); assertEquals(7, top.tiers[0].perks.size)
		assertNull(top.nav.prev); assertEquals(53, top.nav.next)
		assertTrue(top.tiers.flatMap { it.perks }.filter { it.name != "Homing Axe" }.all { it.state == PerkState.LOCKED || it.state == PerkState.ABILITY })
	}

	@Test fun editedTree() {
		val d = RealDumpFixtures.load("hotm")
		val slots = d.slots.map {
			when (it.cleanName) {
				"Mole" -> it.copy(name = "Burrower Supreme", lore = listOf("Level 9/9", "", "Does burrowing.", "", "MAXED"))
				"Mining Fortune" -> it.copy(lore = listOf("Level 3/50", "", "DISABLED", "", "Right-click to enable!"))
				"Tier 5" -> it.copy(name = "Tier 11")
				else -> it
			}
		}.filter { it.slot != 37 } + SlotText(37, "Brand New Perk", listOf("Level 2/7", "", "Cost", "5 Shiny Powder", "", "Left-click to upgrade!"))
		val p = hotm(slots).tiers.flatMap { it.perks }.associateBy { it.name }
		assertEquals(PerkState.MAXED, p.getValue("Burrower Supreme").state)
		assertEquals(PerkState.DISABLED, p.getValue("Mining Fortune").state)
		assertEquals(2 to 7, p.getValue("Brand New Perk").level to p.getValue("Brand New Perk").maxLevel)
		assertEquals("5 Shiny Powder", p.getValue("Brand New Perk").cost)
		assertEquals("Tier 11", hotm(slots).tiers[0].name)
		assertTrue(HeartParser.groups(hotm(slots), true).flatMap { it.tiles }.any { it.label == "§cMining Fortune" })
	}

	@Test fun titlesAndToggle() {
		MenuRegistry.clearForTests(); MenuRegistry.register(HotmMenu); MenuRegistry.register(HotfMenu)
		assertEquals("hotm", MenuRegistry.match("Heart of the Mountain")?.id)
		assertEquals("hotf", MenuRegistry.match("Heart of the Forest")?.id)
		assertNull(MenuRegistry.match("Heart of the Mountain Trees")); assertNull(MenuRegistry.match("Reset Heart of the Mountain"))
		M2.reg(HotmMenu, false); assertTrue(M2.vanilla("Heart of the Mountain"))
		assertNull(HeartParser.parse("Heart of the Forest", listOf(SlotText(45, "Close")), HotfMenu.title))
	}

	@Test fun hotmFromLevelingTasks() {
		MenuRegistry.clearForTests(); MenuRegistry.register(HotmMenu)
		for ((fx, top) in listOf("hotm-from-tasks" to "Tier 5", "hotm-from-tasks-top" to "Tier 10")) {
			val d = RealDumpFixtures.load(fx)
			assertEquals("hotm", MenuRegistry.match(d.title, d.slots)?.id, fx)
			val m = HeartParser.parse(d.title, d.slots, HotmMenu.title)!!
			assertEquals(5, m.tiers.size); assertEquals(top, m.tiers[0].name)
			assertEquals(48, m.nav.back); assertEquals("To Tasks ➜ Mining", m.nav.backLabel); assertEquals(45, m.nav.close)
			assertEquals("Token of the Mountain" to "0", m.resources.first())
		}
		val low = RealDumpFixtures.load("hotm-from-tasks").let { HeartParser.parse(it.title, it.slots, HotmMenu.title)!! }
		assertEquals(hotm().tiers.flatMap { t -> t.perks.map { it.name to it.state } }, low.tiers.flatMap { t -> t.perks.map { it.name to it.state } })
	}

	@Test fun hotmScrolled() {
		MenuRegistry.clearForTests(); MenuRegistry.register(HotmMenu)
		val d = RealDumpFixtures.load("hotm-scrolled")
		assertEquals("hotm", MenuRegistry.match(d.title, d.slots)?.id)
		val m = HeartParser.parse(d.title, d.slots, HotmMenu.title)!!
		assertEquals(listOf(TierState.LOCKED, TierState.LOCKED, TierState.LOCKED, TierState.PROGRESS, TierState.UNLOCKED), m.tiers.map { it.state })
		assertEquals(listOf("Tier 9", "Tier 8", "Tier 7", null, null), m.tiers.map { it.requires })
		assertEquals("Tier 10 §8locked §7(needs Tier 9)", HeartParser.tierName(m.tiers[0]))
		assertEquals("45.6% 68,366/150k", m.tiers[3].progress); assertNull(m.tiers[0].progress)
		val locked = m.tiers.take(3).flatMap { it.perks }
		assertTrue(locked.isNotEmpty() && locked.all { it.state == PerkState.LOCKED && it.requires != null })
		assertTrue(m.tiers[4].perks.none { it.state == PerkState.LOCKED && it.requires?.startsWith("Tier") == true })
		assertNull(m.nav.prev); assertEquals(53, m.nav.next)
		val mid = RealDumpFixtures.load("hotm-scrolled-mid").let { HeartParser.parse(it.title, it.slots, HotmMenu.title)!! }
		assertEquals((6 downTo 2).map { "Tier $it" }, mid.tiers.map { it.name })
		assertEquals(TierState.UNLOCKED, mid.tiers[0].state); assertEquals(53, mid.nav.next)
		assertTrue(mid.buttons.none { it.cleanName.startsWith("Scroll") })
	}
}
