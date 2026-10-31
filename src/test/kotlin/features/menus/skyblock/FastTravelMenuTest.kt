// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import net.minecraft.core.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.FastTravelParser
import dev.ashtonite365.apex.features.misc.WarpMenu
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class FastTravelMenuTest {
	private val d = RealDumpFixtures.load("fast-travel")

	@Test
	fun realDumpTiles() {
		val m = FastTravelParser.parse(d.title, d.slots)!!
		assertEquals(19, m.tiles.size)
		val byName = m.tiles.associateBy { it.slot }
		assertEquals(listOf("home"), byName[10]!!.warps); assertTrue(byName[10]!!.here)
		assertEquals("Mining Tier III", byName[21]!!.info)
		assertTrue(byName[30]!!.locked, "Rift: warp not unlocked")
		assertTrue(byName[16]!!.locked, "undiscovered")
		assertEquals("Jerry's Workshop", byName[33]!!.island); assertTrue(byName[33]!!.locked)
		assertFalse(byName[29]!!.locked)
		assertEquals(mapOf("Island Browser" to 45, "Go Back" to 48, "Close" to 49, "Advanced Mode" to 50), m.nav)
	}

	@Test
	fun titleAndMismatch() {
		assertTrue(FastTravelParser.TITLE.containsMatchIn("Fast Travel"))
		assertFalse(FastTravelParser.TITLE.containsMatchIn("Warps"))
		assertNull(FastTravelParser.parse(d.title, d.slots.filter { it.lore.none { l -> l.startsWith("/warp") } }))
		assertNull(FastTravelParser.parse("Settings", d.slots))
	}

	@Test
	fun mergesFancyWarpEntriesWithoutDuplicates() {
		val e = listOf(WarpMenu.Entry("hub", "hub", emptyList(), BlockPos.ZERO), WarpMenu.Entry("castle", "hub", emptyList(), BlockPos.ZERO),
			WarpMenu.Entry("forge", "mining_3", emptyList(), BlockPos.ZERO), WarpMenu.Entry("dwarves", "mining_3", emptyList(), BlockPos.ZERO))
		assertEquals(listOf("castle"), FastTravelParser.repoWarps(listOf("hub"), e).map { it.warp })
		assertEquals(listOf("forge"), FastTravelParser.repoWarps(listOf("dwarves"), e).map { it.warp })
		assertTrue(FastTravelParser.repoWarps(listOf("garden"), e).isEmpty())
	}
}
