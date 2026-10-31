package dev.ashtonite365.apex.test.features.menus.levelling

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.h6.StatsParser
import dev.ashtonite365.apex.features.menus.levelling.EmblemsMenu
import dev.ashtonite365.apex.features.menus.levelling.EmblemsParser
import dev.ashtonite365.apex.features.menus.levelling.LevelingRewardsParser
import dev.ashtonite365.apex.features.menus.levelling.LevellingParser
import dev.ashtonite365.apex.features.menus.levelling.RewardsMenu
import dev.ashtonite365.apex.features.menus.levelling.RewardsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class LevellingUpgradeTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private fun d(n: String) = RealDumpFixtures.load(n)

	@Test fun levelWindowAndMilestone() {
		val x = d("skyblock-levelling-1045"); val m = LevellingParser.parse(x.title, x.slots)!!

		assertEquals(listOf(122, 123, 124, 125, 126), m.track.map { it.level })
		assertEquals("Your Level", m.track.first().tag); assertTrue(m.track.first().unlocked)
		assertEquals(140, m.milestone!!.level); assertEquals("1,741 XP (18 Levels)", m.milestone!!.xpLeft)
		assertEquals(59.0, m.track.first { it.level == 123 }.progress!!.current)

		assertTrue(m.track.first { it.level == 125 }.rewards.any { it.contains("Strength") })
		assertFalse(m.track.first { it.level == 124 }.rewards.any { it.contains("Strength") })
	}

	@Test fun levelingRewardCategoriesDiscovered() {
		val x = d("leveling-rewards-1045"); val m = LevelingRewardsParser.parse(x.title, x.slots)!!
		val names = x.slots.filter { s -> s.cleanLore.any { it == "Next Reward:" } }.map { it.cleanName }
		assertEquals(names, m.categories.map { it.name }); assertEquals(5, names.size)
		val feat = m.categories.first { it.slot == 11 }
		assertEquals("50B Auction House Bid Limit" to 200, feat.next); assertEquals(11.0, feat.unlocked!!.current); assertEquals(12.0, feat.unlocked!!.needed)
		val stat = m.categories.first { it.slot == 14 }
		assertNull(stat.unlocked); assertEquals(listOf("every level: +5 Health", "every 5 levels: +1 Strength"), stat.recurring.map { it.replace(Regex("\\s*\\p{Co}"), "") })
	}

	@Test fun rewardsCategoryPages() {
		MenuRegistry.register(RewardsMenu)
		for ((f, header) in listOf("leveling-rewards-feature-1045" to "Rewards", "leveling-rewards-bonus-1045" to "Rewards")) {
			val x = d(f); val m = RewardsParser.parse(x.title, x.slots)!!
			assertEquals(header, m.category)
			assertEquals(x.slots.count { s -> s.slot != 4 && s.cleanLore.firstOrNull()?.startsWith("Level ") == true }, m.rewards.size, f)
			assertTrue(m.done.isNotEmpty() && m.upcoming.isNotEmpty(), f)
			assertEquals("leveling-reward-list", MenuRegistry.match(x.title, x.slots)?.id, f)
		}
		val bonus = d("leveling-rewards-bonus-1045").let { RewardsParser.parse(it.title, it.slots)!! }
		assertEquals(150, bonus.upcoming.first().level); assertEquals(122.0, bonus.upcoming.first().progress!!.current)

		assertNull(RewardsParser.parse("Rewards", listOf(dev.ashtonite365.apex.features.menus.support.SlotText(13, "Diamond", listOf("Click to claim!")))))
	}

	@Test fun levelNRewards() {
		for ((f, n) in listOf("level-140-rewards-1048" to 140, "level-122-rewards-1048" to 122, "level-125-rewards-1048" to 125)) {
			val x = d(f); val m = RewardsParser.parse(x.title, x.slots)!!
			assertEquals("Level $n Rewards", m.category, f)
			assertTrue(m.rewards.all { it.level == n }, f)
			assertEquals(x.slots.count { s -> s.cleanLore.firstOrNull() == "Level $n" }, m.rewards.size, f)
		}
		assertTrue(d("level-122-rewards-1048").let { RewardsParser.parse(it.title, it.slots)!! }.rewards.all { it.unlocked })
		assertTrue(d("level-140-rewards-1048").let { RewardsParser.parse(it.title, it.slots)!! }.rewards.none { it.unlocked })
	}

	@Test fun emblemsOverviewVsCategoryBySlots() {
		MenuRegistry.register(EmblemsMenu)
		val o = d("emblems-overview-1048"); val om = EmblemsParser.parse(o.title, o.slots)!!
		assertTrue(om.isOverview); assertEquals(7, om.groups.size)
		assertEquals(o.slots.sumOf { s -> s.cleanLore.firstNotNullOfOrNull { Regex("^(\\d+) Unlocked$").matchEntire(it)?.groupValues?.get(1)?.toInt() } ?: 0 }, om.groups.sumOf { it.unlocked ?: 0 })
		assertEquals("Star ⚝", om.selected!!.name); assertTrue(om.selected!!.preview!!.contains("Ashtonite365"))
		val lv = d("emblems-leveling-1048"); val lm = EmblemsParser.parse(lv.title, lv.slots)!!
		assertFalse(lm.isOverview); assertTrue(lm.emblems.any { it.selected && it.name == "Star ⚝" })
		assertEquals(lv.slots.count { s -> s.cleanLore.firstOrNull()?.startsWith("Requires") == true || s.cleanLore.firstOrNull() == "Locked" }, lm.emblems.size)
		val cat = d("emblems-catacombs-1048"); val cm = EmblemsParser.parse(cat.title, cat.slots)!!
		assertTrue(cm.emblems.first().locked); assertEquals("Requires Catacombs Skill 40", cm.emblems.first().requirement)
		val p1 = d("emblems-achievement-p1-1048"); val p1m = EmblemsParser.parse(p1.title, p1.slots)!!
		assertEquals(1 to 2, p1m.page); assertNotNull(p1m.nav["Next Page"])
		assertEquals(2 to 2, d("emblems-achievement-p2-1048").let { EmblemsParser.parse(it.title, it.slots)!!.page })
		assertEquals(1, d("emblems-discord-1048").let { EmblemsParser.parse(it.title, it.slots)!!.emblems.size })
		assertTrue(d("emblems-slayer-1048").let { EmblemsParser.parse(it.title, it.slots)!! }.emblems.any { !it.locked && it.stats.isNotEmpty() })
		for (f in listOf("emblems-overview-1048", "emblems-catacombs-1048", "emblems-achievement-p2-1048"))
			assertEquals("emblems", d(f).let { MenuRegistry.match(it.title, it.slots)?.id }, f)
	}

	@Test fun statsIsH6() {
		val x = d("stats-equipment-1045")
		assertNotNull(StatsParser.parse(x.title, x.slots))
	}
}
