// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenProgress
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class GardenMelonMilestoneTest {
	@Test fun melonSliceMenuName() {
		assertEquals(Crop.MELON to 15, Crop.fromMenuName("Melon Slice XV"))
		assertEquals(Crop.MELON to 15, Crop.fromMenuName("Melon XV"))
		assertEquals(Crop.SUGAR_CANE to 14, Crop.fromMenuName("Sugar Cane XIV"))
		assertEquals(Crop.COCOA_BEANS to 12, Crop.fromMenuName("Cocoa Beans XII"))
		assertEquals(Crop.MUSHROOM to 0, Crop.fromMenuName("Mushroom"))
		assertNull(Crop.fromMenuName("Go Back"))
	}

	@Test fun everyCropInTheRealMenuResolves() {
		val d = RealDumpFixtures.load("garden-crop-milestones")
		val names = d.slots.map { it.name }
		for (c in listOf("Wheat", "Carrot", "Potato", "Pumpkin", "Sugar Cane", "Melon Slice", "Cactus", "Cocoa Beans"))
			assertEquals(true, names.any { n -> n.startsWith(c) && Crop.fromMenuName(n) != null }, c)
	}

	@Test fun totalLineWins() {
		val lore = listOf("Harvest Melon Slice on your Garden, to increase your Melon Slice tier!", "", "Total: 521,175", "",
			"Progress to Tier XVI: 21.6%", "                     54,125/250k")
		assertEquals(521_175L, GardenProgress.totalFromMenu(listOf(1L, 2L), 15, lore))
		val melon = RealDumpFixtures.load("garden-crop-milestones").slots.first { it.name.startsWith("Melon Slice") }
		assertEquals(26_018L, GardenProgress.totalFromMenu(emptyList(), 7, melon.lore))
	}

	@Test fun liveCounterDelta() {
		assertEquals(40L, GardenProgress.counterDelta(100L to 500L, 140L to 520L))
		assertEquals(25L, GardenProgress.counterDelta(100L to 500L, 100L to 525L), "a stale counter doesn't block the moving one")
		assertEquals(10L, GardenProgress.counterDelta(null to 5L, null to 15L))
		assertEquals(0L, GardenProgress.counterDelta(null to null, 3L to null))
	}
}
