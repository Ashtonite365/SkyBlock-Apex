// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.DragonTracker
import dev.ashtonite365.apex.features.misc.SpecialZealot
import dev.ashtonite365.apex.features.world.BlockPathPlanner
import dev.ashtonite365.apex.features.world.FairySoulPathfinder
import dev.ashtonite365.apex.features.world.SoulAStar
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.util.SkyBlockIsland

class SpecialZealotTest {

	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	@Test
	fun chatLineWithFormattingVariants() {
		assertTrue(SpecialZealot.isSpawnLine("A special Zealot has spawned nearby!"))
		assertTrue(SpecialZealot.isSpawnLine("§5A special §dZealot§5 has spawned nearby!"))
		assertTrue(SpecialZealot.isSpawnLine("  §r§5§lA special Zealot has spawned nearby!§r  "))
		assertTrue(SpecialZealot.isSpawnLine("A  Special   zealot has spawned nearby!!"))
		assertTrue(SpecialZealot.isSpawnLine("A SPECIAL ZEALOT HAS SPAWNED NEARBY"))
		assertFalse(SpecialZealot.isSpawnLine("Steve: A special Zealot has spawned nearby!"))
		assertFalse(SpecialZealot.isSpawnLine("A Zealot has spawned nearby!"))
		assertFalse(SpecialZealot.isSpawnLine("RARE DROP! Summoning Eye"))
	}

	@Test
	fun carriedBlockDetection() {
		val frame = Blocks.END_PORTAL_FRAME.defaultBlockState()
		assertTrue(SpecialZealot.isSpecialCarriedBlock(frame))
		assertFalse(SpecialZealot.isSpecialCarriedBlock(null))
		assertFalse(SpecialZealot.isSpecialCarriedBlock(Blocks.END_STONE.defaultBlockState()))
		assertFalse(SpecialZealot.isSpecialCarriedBlock(Blocks.OBSIDIAN.defaultBlockState()))
		assertTrue(SpecialZealot.isSpecialZealot(SkyBlockIsland.END, frame))
		assertTrue(SpecialZealot.isSpecialZealot(SkyBlockIsland.ofMode("combat_3"), frame))
		assertFalse(SpecialZealot.isSpecialZealot(SkyBlockIsland.HUB, frame))
		assertFalse(SpecialZealot.isSpecialZealot(null, frame))
		assertFalse(SpecialZealot.isSpecialZealot(SkyBlockIsland.END, Blocks.END_STONE.defaultBlockState()))
	}

	@Test
	fun dedupePerEntityAndCooldown() {
		val d = SpecialZealot.Deduper(cooldownMs = 5_000)
		assertTrue(d.onEntity(42, 1_000))
		assertFalse(d.onEntity(42, 1_100))
		assertFalse(d.onEntity(42, 60_000))
		assertFalse(d.onChat(2_000))
		assertTrue(d.onEntity(43, 3_000))
		assertTrue(d.onChat(20_000))
		assertFalse(d.onChat(21_000))
		assertFalse(d.onEntity(44, 22_000))
		assertTrue(d.onEntity(46, 22_500))
		assertFalse(d.onEntity(44, 40_000))
		assertTrue(d.onEntity(45, 40_000))
		d.reset()
		assertTrue(d.onEntity(42, 100_000))
	}

	@Test
	fun bothOffByDefaultWithWarningInCombat() {
		val c = DragonTracker.TConfig
		for (key in listOf("special-zealot-alert", "special-zealot-path")) {
			val o = c.allOptions.getValue(key)
			assertEquals(false, o.default(), key)
			assertEquals(RiskLevel.RISKY, o.riskLevel, key)

			assertEquals(RiskLevel.NONE, o.shownRisk, key)
			assertFalse(o.labelText.string.startsWith("⚠"), key)
		}
		for (key in listOf("special-zealot-sound", "special-zealot-title")) {
			val o = c.allOptions.getValue(key)
			assertTrue(o.riskLevel.isRisky, key)
			assertSame(c.allOptions.getValue("special-zealot-alert"), o.parentToggle, key)
		}
		assertEquals(true, c.allOptions.getValue("zealots").default())
		assertEquals(RiskLevel.NONE, c.allOptions.getValue("zealots").riskLevel)
		val map = MigrationMap.bundled()
		for (key in listOf("special-zealot-alert", "special-zealot-sound", "special-zealot-title", "special-zealot-path", "special-zealot-path-colour")) {
			val e = map["dragon-tracker.$key"]!!
			assertEquals(MainSection.MISC, e.newPath.main, key)
			assertEquals("the-end", e.newPath.subsection, key)
			assertEquals("combat", e.group, key)
		}
	}

	private val floor = object : BlockPathPlanner.Source {
		override fun columnLoaded(x: Int, z: Int) = true
		override fun cell(x: Int, y: Int, z: Int): Byte = when {
			y <= 63 -> SoulAStar.SOLID
			x == 10 && z in -3..3 && y <= 66 -> SoulAStar.SOLID
			else -> SoulAStar.AIR
		}
	}

	@Test
	fun pathRequestsGoThroughTheSharedPlanner() {
		val planner = SpecialZealot.newPlanner().also { it.synchronous = true }
		assertEquals(FairySoulPathfinder.NODE_CAP, planner.nodeCap)
		assertEquals(FairySoulPathfinder.MAX_DIST, planner.maxDist)
		assertFalse(planner.straightFallback)
		assertEquals(FairySoulPathfinder.NODE_CAP, SpecialZealot.pathTracker.planner.nodeCap)
		val t = SpecialZealot.PathTracker(planner)
		val feet = BlockPos(0, 64, 0)
		t.update(feet, BlockPos(20, 64, 0), floor, 0, 128)
		assertEquals(1, planner.plansStarted)
		val route = t.route!!
		assertTrue(route.size > 2)
		assertTrue(route.none { it.x in 10.0..11.0 && it.z in -3.0..4.0 }, "route goes around the wall")

		t.update(feet, BlockPos(21, 64, 0), floor, 0, 128)
		assertEquals(BlockPos(20, 64, 0), t.target)
		assertEquals(1, planner.plansStarted)

		repeat(10) { t.update(feet, BlockPos(21, 64, 0), floor, 0, 128) }
		t.update(feet, BlockPos(20, 64, 12), floor, 0, 128)
		assertEquals(BlockPos(20, 64, 12), t.target)
		assertEquals(2, planner.plansStarted)
		assertNotNull(t.route)

		t.update(feet, null, floor, 0, 128)
		assertNull(t.route); assertNull(planner.result); assertNull(t.target)
		t.update(feet, BlockPos(500, 64, 0), floor, 0, 128)
		assertNull(t.route); assertEquals(2, planner.plansStarted)
	}
}
