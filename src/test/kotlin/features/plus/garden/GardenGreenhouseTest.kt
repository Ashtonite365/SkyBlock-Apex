// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.core.BlockPos
import dev.ashtonite365.apex.features.garden.GardenGreenhouse as G

class GardenGreenhouseTest {
	@Test
	fun phantomleaf() {
		assertEquals(15.0, G.distanceFromVolume(0.5f), 1e-6)
		val c = G.candidatesAt(0.5, 0.5, 5.0)
		assertTrue(BlockPos(5, 74, 0) in c)
		assertTrue(BlockPos(3, 74, 4) in c)
		assertTrue(c.all { it.y == 74 })
	}

	@Test
	fun status() {
		assertEquals(G.Status.HARVESTABLE, G.harvestStatus(listOf("Crop", "Status: Harvestable")))
		assertEquals(G.Status.DROPS, G.harvestStatus(listOf("Drops: 3x Wheat")))
		assertEquals(G.Status.GROWING, G.harvestStatus(listOf("Status: Growing")))
		assertTrue(G.hasEnoughWater(listOf("This crop doesn't need water!")))
	}
}
