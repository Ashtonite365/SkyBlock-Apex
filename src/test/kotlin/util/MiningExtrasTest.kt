/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.mining.MiningExtras
import dev.ashtonite365.apex.features.mining.MiningSolvers

class MiningExtrasTest {
	@Test
	fun parsers() {
		assertTrue(MiningExtras.isWorm("You hear the sound of something approaching..."))
		assertFalse(MiningExtras.isWorm("You hear nothing"))
		assertEquals("UMBER", MiningExtras.parseCorpse("  UMBER CORPSE LOOT! "))
		assertEquals("Skeleton Key", MiningExtras.corpseKey("VANGUARD"))
		assertNull(MiningExtras.corpseKey("LAPIS"))
		assertEquals("Sorrow", MiningExtras.parseGhostDrop("RARE DROP! Sorrow (+120% ✯ Magic Find)"))
		assertTrue(MiningExtras.matchesName("§cGlacite Walker 120♥", "Glacite Walker|Goblin"))
	}

	@Test
	fun solvers() {
		assertEquals("20x Mithril", MiningSolvers.fetchurAnswer("[NPC] Fetchur: theyre expensive minerals"))
		assertNull(MiningSolvers.fetchurAnswer("[NPC] Fetchur: something unknown"))
		assertEquals(net.minecraft.core.BlockPos(182, 195, 136), MiningSolvers.puzzlerTarget("▲◀▲▼"))
		assertEquals(12, MiningSolvers.perkLevel(listOf("Level 12/50"), "Mining Speed"))
		assertEquals(3, MiningSolvers.perkLevel(listOf(), "Tier 3"))
	}

	@Test
	fun dataTables() {
		assertEquals("1x Emerald", MiningSolvers.fetchurAnswer("[NPC] Fetchur: its green and some dudes trade stuff for it"))
		assertEquals(net.minecraft.core.BlockPos(-2, 142, 437), MiningSolvers.parseWaypoint("-2:142:437"))
		val json = com.google.gson.JsonParser.parseString("""{"A": {"title": "Onyx", "waypoints": ["1:2:3", "bad"]}}""").asJsonObject
		assertEquals(listOf("Onyx" to net.minecraft.core.BlockPos(1, 2, 3)), MiningSolvers.gemstoneWaypoints(json))
	}

	@Test
	fun hotmCosts() {
		val pre = listOf("(defun min (l r) (if (lt l r) l r))", "(defun npi (level0 maxLevel) (if (= level0 0) :COAL :EMERALD))")
		assertEquals(5.0, dev.ashtonite365.apex.util.math.TinyLisp.evaluate(pre, "(min 5 9)", emptyMap()))
		assertEquals(":COAL", dev.ashtonite365.apex.util.math.TinyLisp.evaluate(pre, "(npi level0 maxLevel)", mapOf("level0" to 0.0, "maxLevel" to 50.0)))
		val perk = dev.ashtonite365.apex.features.mining.HotmCostTooltip.Perk("Mining Speed", 3, "MITHRIL", "(pow (+ level 2) 3)")
		assertEquals(27L, dev.ashtonite365.apex.features.mining.HotmCostTooltip.costAt(pre, perk, 1))
		assertEquals(27L + 27L + 64L, dev.ashtonite365.apex.features.mining.HotmCostTooltip.costToMax(pre, perk, 0))
	}

	@Test
	fun miningLoot() {
		val L = dev.ashtonite365.apex.features.mining.MiningLoot
		assertEquals(dev.ashtonite365.apex.features.mining.MiningLoot.Source.CORPSE, L.header("  UMBER CORPSE LOOT! "))
		assertEquals(dev.ashtonite365.apex.features.mining.MiningLoot.Source.NUCLEUS, L.header("CRYSTAL NUCLEUS LOOT BUNDLE"))
		assertEquals("Umber" to 12, L.reward("    Umber x12"))
		assertEquals("Fine Onyx Gemstone" to 1, L.reward("  Fine Onyx Gemstone"))
		assertEquals(null, L.reward("▬▬▬▬"))
		assertEquals(1234L, L.ghostCoins("The ghost's death materialized 1,234 coins from the mists!"))
		assertEquals(setOf(1, 2), L.compactDrill(listOf("Fuel: 3,000/3,000", "Fuel Tank: Not Installed", "Increases the fuel capacity.", "Drill Engine: Titanium")))
	}

	@Test
	fun fossilAndCorpses() {
		val F = dev.ashtonite365.apex.features.mining.FossilExcavator
		val T = dev.ashtonite365.apex.features.mining.FossilExcavator.Tile.UNKNOWN
		val open = List(F.W * F.H) { T }
		val all = F.solve(open)
		assertTrue(all.placements > 0)
		assertEquals(8, all.names.size)
		val helix = F.solve(open, "7.1")
		assertEquals(setOf(dev.ashtonite365.apex.features.mining.FossilExcavator.Fossil.HELIX), helix.names)
		assertEquals(14, dev.ashtonite365.apex.features.mining.FossilExcavator.Fossil.HELIX.base.size)
		assertEquals("§6Umber", dev.ashtonite365.apex.features.mining.MiningRisky.corpseType("Mineral Helmet"))
	}
}
