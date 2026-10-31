// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.*
import dev.ashtonite365.apex.features.menus.support.SlotDumpGate
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.MenuRegistry

class MilestonesTest {
	private val slot48 = listOf("§7Total XP: §b337§7/§b3,646", "§7Milestone: §a3§7/§a40", "", "§7Progress to Milestone 4: §e37%")
	private fun ctrls() = listOf(SlotText(48, "Go Back"), SlotText(49, "Close"), SlotText(53, "Scroll Right", listOf("Left-click to scroll", "Right-click to scroll fast")))

	@Test fun progressParse() {
		val p = MilestonesParser.progress(slot48)!!
		assertEquals(337, p.xp); assertEquals(3646, p.xpMax); assertEquals(3, p.milestone); assertEquals(40, p.milestones); assertEquals(37, p.nextPct)
	}

	@Test fun statesFor3of40() {
		val s = MilestonesParser.states(3, emptyMap())
		assertEquals(40, s.size)
		assertEquals(List(3) { MilestoneState.DONE }, s.take(3))
		assertEquals(MilestoneState.IN_PROGRESS, s[3])
		assertTrue(s.drop(4).all { it == MilestoneState.LOCKED })
		val o = MilestonesParser.states(3, mapOf(4 to MilestoneState.DONE))
		assertEquals(MilestoneState.DONE, o[3])
	}

	@Test fun rewardTable() {
		val base = listOf("+1% Bits Multiplier", "+2% Bank Interest")
		assertEquals(base, MilestoneRewards.rewards(1))
		assertEquals(base + "Travel Scroll to Museum", MilestoneRewards.rewards(5))
		assertEquals(base + "Hex", MilestoneRewards.rewards(8))
		assertTrue(MilestoneRewards.rewards(3).containsAll(listOf("Deluxe Bank Upgrade", "Museum Appraisal Service")))
		assertTrue(MilestoneRewards.rewards(10).size == 6)
		assertTrue(MilestoneRewards.rewards(24).any { "Golden Collecting Top Hat" in it })
		assertEquals(400, MilestoneRewards.xpNeeded(4))
	}

	@Test fun tileColoursAndReparseInPlace() {
		val a = MilestonesParser.parse("Museum Milestones", listOf(SlotText(10, "Milestone 1"), SlotText(11, "Milestone 4")) + ctrls(),
			mapOf(10 to TileColour.GREEN, 11 to TileColour.YELLOW))!!
		assertEquals(listOf(1, 4), a.tiles.map { it.level })
		assertEquals(MilestoneState.DONE, a.tiles[0].state); assertEquals(MilestoneState.IN_PROGRESS, a.tiles[1].state)
		assertEquals(53, a.scrollRightSlot); assertEquals(48, a.backSlot); assertEquals(49, a.closeSlot)

		val b = MilestonesParser.parse("Museum Milestones", listOf(SlotText(10, "Milestone 12"), SlotText(11, "Milestone 13")) + ctrls(),
			mapOf(10 to TileColour.RED, 11 to TileColour.RED))!!
		assertEquals(listOf(12, 13), b.tiles.map { it.level })
		assertTrue(b.tiles.all { it.state == MilestoneState.LOCKED })
		assertEquals(TileColour.GREEN, MilestonesMenu.colourOf("lime_stained_glass_pane"))
		assertEquals(TileColour.RED, MilestonesMenu.colourOf("red_stained_glass_pane"))
		assertNull(MilestonesMenu.colourOf("barrier"))
	}

	@Test fun dumpEveryUpdate() {
		val g = SlotDumpGate()
		assertFalse(g.offer(7, 1, 10, 0, everyUpdate = true)); assertTrue(g.offer(7, 1, 10, 400, everyUpdate = true))
		assertFalse(g.offer(7, 2, 10, 500, everyUpdate = true)); assertTrue(g.offer(7, 2, 10, 900, everyUpdate = true))
		val once = SlotDumpGate()
		once.offer(7, 1, 10, 0); assertTrue(once.offer(7, 1, 10, 400))
		once.offer(7, 2, 10, 500); assertFalse(once.offer(7, 2, 10, 900))
	}

	@Test fun cacheMerge() {
		val d = MilestoneCacheData()
		assertTrue(MilestoneCacheData.merge(d, MilestonesParser.progress(slot48)!!))
		assertEquals(3, d.progress!!.milestone)
		MilestoneCacheData.merge(d, MilestoneProgress(milestone = 4))
		assertEquals(337, d.progress!!.xp); assertEquals(4, d.progress!!.milestone)
	}

	@Test fun titleIsMuseumNotOthers() {
		assertTrue(MilestonesParser.TITLE.matches(MenuRegistry.strip("Museum Milestones")))
		assertFalse(dev.ashtonite365.apex.features.menus.museum.MuseumParser.ANY_TITLE.matches("Museum Milestones"))
	}
}

class MilestoneClaimTest {
	private fun page(vararg levels: Int, right: Int? = 53, left: Int? = 45) = MilestonesPage(
		levels.mapIndexed { i, lv -> MilestoneTile(10 + i, lv, "Milestone $lv", emptyList(), null) }, null, right, left, 48, 49)

	@Test fun liveTileDirectClick() {
		assertEquals(11, MilestoneClaim.decide(2, page(1, 2, 3)))
	}

	@Test fun offPageSendsNothing() {
		assertEquals(null, MilestoneClaim.decide(20, page(1, 2, 3)))
		assertEquals(null, MilestonesMenuSettings.allOptions["claim-chain"])
	}
}
