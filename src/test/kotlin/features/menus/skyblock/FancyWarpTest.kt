// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.menus.skyblock.DrawRect
import dev.ashtonite365.apex.features.menus.skyblock.FancyWarp
import dev.ashtonite365.apex.features.menus.skyblock.FancyWarpLayout
import dev.ashtonite365.apex.features.menus.skyblock.FastTravelParser
import dev.ashtonite365.apex.features.menus.skyblock.NPoint
import dev.ashtonite365.apex.features.menus.skyblock.WarpPlan
import dev.ashtonite365.apex.features.menus.support.FallbackPlanner
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.misc.WarpMenu
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.util.ApexClickDelay

class FancyWarpTest {
	private val d = RealDumpFixtures.load("fast-travel")
	private val model = FastTravelParser.parse(d.title, d.slots)!!
	private fun e(warp: String, mode: String, vararg al: String) = WarpMenu.Entry(warp, mode, al.toList(), BlockPos.ZERO)
	private val repo = listOf(e("hub", "hub"), e("castle", "hub", "Castle"), e("museum", "hub"), e("crypt", "hub", "Crypts"), e("garden", "garden"), e("plots", "garden"))
	private fun labels(slots: List<SlotText>) = slots.filter { it.cleanName.isNotBlank() }.map { MenuChain.Label(it.slot, it.cleanName, it.cleanLore) }

	private val syntheticHubSubMenu = listOf(
		SlotText(10, "SkyBlock Hub", listOf("/warp hub", "", "Click to warp!")),
		SlotText(11, "Castle", listOf("/warp castle", "", "Click to warp!")),
		SlotText(12, "Crypts", listOf("/warp crypt", "", "Warp not unlocked!")),
		SlotText(13, "Museum", listOf("", "Click to warp!")),
		SlotText(31, "Go Back", listOf("To Fast Travel")),
	)

	@Test
	fun liveParseIslandsAndWarps() {
		val isl = FancyWarp.build(model, repo).associateBy { it.tileSlot }
		assertEquals("hub", isl[11]!!.key)
		assertEquals(listOf("hub", "castle", "museum", "crypt"), isl[11]!!.warps.map { it.warp })
		assertTrue(isl[11]!!.warps.all { it.viaSubmenu }, "Hub tile says Left-Click to open!")
		assertEquals("private island", isl[10]!!.key); assertTrue(isl[10]!!.here)
		assertEquals("galatea", isl[15]!!.key, "Moonglade Marsh sits on the Galatea spot")
		assertFalse(isl[12]!!.warps.single().viaSubmenu, "Dungeon Hub: Click to warp!")

		val plots = isl[29]!!.warps.first { it.warp == "plots" }
		assertFalse(plots.reachable); assertTrue(FancyWarp.plan(plots) is WarpPlan.Blocked)

		val d2 = RealDumpFixtures.load("fast-travel-1436")
		val isl2 = FancyWarp.build(FastTravelParser.parse(d2.title, d2.slots)!!, emptyList()).associateBy { it.tileSlot }
		assertFalse(isl2[10]!!.here); assertEquals(WarpPlan.Direct(10), FancyWarp.plan(isl2[10]!!.warps.single()))
		assertEquals(isl.keys, isl2.keys)
	}

	@Test
	fun lockedDimmedWithLore() {
		val all = FancyWarp.build(model, emptyList())
		val rift = all.first { it.tileSlot == 30 }.warps.single()
		assertTrue(rift.locked); assertFalse(FancyWarp.clickable(rift), "dimmed + unclickable")
		assertTrue(rift.lore.any { it.contains("Warp not unlocked", true) }); assertTrue(rift.lore.any { it.contains("Roger", true) }, "requirement lore in the tooltip")
		assertEquals(WarpPlan.Blocked("locked"), FancyWarp.plan(rift))
		val jerry = all.first { it.tileSlot == 33 }
		assertTrue(jerry.locked); assertEquals("jerry's workshop", jerry.key)
		assertTrue(all.first { it.tileSlot == 16 }.locked, "undiscovered")
		assertTrue(FancyWarp.clickable(all.first { it.tileSlot == 12 }.warps.single()))
	}

	@Test
	fun portalClickMapping() {
		val isl = FancyWarp.build(model, repo).associateBy { it.tileSlot }
		assertEquals(WarpPlan.Direct(12), FancyWarp.plan(isl[12]!!.warps.single()), "real slot 12, one user click")
		val castle = isl[11]!!.warps.first { it.warp == "castle" }

		assertEquals(WarpPlan.Command("/warp castle"), FancyWarp.plan(castle))
		for (w in isl[11]!!.warps) assertEquals(WarpPlan.Command("/warp ${w.warp}"), FancyWarp.plan(w))
		assertNotNull(FancyWarpLayout.portal("castle")); assertNotNull(FancyWarpLayout.portal("dungeon_hub"))

		assertEquals(13, FancyWarp.pickSub(labels(syntheticHubSubMenu), "museum", "Museum")?.slot)
		assertNull(FancyWarp.pickSub(labels(syntheticHubSubMenu), "wizard", "wizard"))
	}

	@Test
	fun unmarkedListedAndLogged() {
		val isl = FancyWarp.build(model, emptyList())

		assertEquals(emptyList<String>(), FancyWarp.unmarked(isl))
		assertTrue(isl.first { it.tileSlot == 21 }.warps.isNotEmpty(), "still listed")
		val off = isl.filter { !FancyWarpLayout.onImage(it.key) }.map { it.name }.toSet()
		assertEquals(setOf("Dwarven Mines", "Crystal Hollows", "The Rift", "Undiscovered Island"), off)
	}

	@Test
	fun portalsScaleWithDrawRect() {
		val p = NPoint(0.25, 0.5)
		assertEquals(110 to 70, DrawRect(10, 20, 400, 100).map(p))
		assertEquals(220 to 140, DrawRect(20, 40, 800, 200).map(p))
		val a = DrawRect(0, 0, 320, 180); val b = DrawRect(0, 0, 960, 540); val moved = DrawRect(37, 11, 320, 180)
		for ((w, n) in FancyWarpLayout.PORTALS) {
			val (ax, ay) = a.map(n); val (bx, by) = b.map(n); val (cx, cy) = moved.map(n)
			assertTrue(kotlin.math.abs(bx - ax * 3) <= 2 && kotlin.math.abs(by - ay * 3) <= 2, "$w scales")
			assertEquals(ax + 37 to ay + 11, cx to cy, "$w moves with the rect")
			assertTrue(n.x in 0.0..1.0 && n.y in 0.0..1.0, w)
		}
		val r = FancyWarp.fit(0, 0, 500, 500)
		assertEquals(500, r.w); assertEquals(281, r.h)
		val r2 = FancyWarp.fit(0, 0, 1000, 200)
		assertEquals(355, r2.w); assertEquals(200, r2.h); assertEquals((1000 - 355) / 2, r2.x)

		val pts = listOf("a" to (10 to 10), "b" to (20 to 10))
		assertEquals("b", FancyWarp.nearest(pts, { it.second }, 17, 11, 6)?.first)
		assertNull(FancyWarp.nearest(pts, { it.second }, 40, 40, 6))
		assertTrue(FancyWarp.radius(960) > FancyWarp.radius(320))
	}

	@Test
	fun delayRangeRerolled() {
		val seen = (1..400).map { ApexClickDelay.nextMs() }
		assertTrue(seen.all { it in 100L..200L }); assertTrue(seen.toSet().size > 10, "re-rolled per click")
		val got = mutableListOf<Long>()
		ApexClickDelay.scheduler = { ms, _ -> got += ms }
		try { ApexClickDelay.later {}; ApexClickDelay.laterMs(5) {}; ApexClickDelay.laterMs(999) {} } finally { ApexClickDelay.resetForTests() }
		assertTrue(got[0] in 100L..200L); assertEquals(listOf(100L, 200L), got.drop(1))
	}

	@Test
	fun fallsBackToNearestApexParent() {
		val planner = FallbackPlanner({ it == "SkyBlock Menu" })
		assertEquals(FallbackPlanner.Decision.GoBack(48, "SkyBlock Menu"), planner.decide(d.title, d.slots, 0))
	}
}
