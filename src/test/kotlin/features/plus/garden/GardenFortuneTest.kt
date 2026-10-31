// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenFortune
import dev.ashtonite365.apex.features.garden.GardenProgress
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop

class GardenFortuneTest {
	@Test
	fun tab() {
		val tab = listOf("Stats:", " Farming Fortune: ☘1234", " Wheat Fortune: ☘56", " Speed: ✦400")
		assertEquals(1290.0, GardenFortune.tabFortune(tab, Crop.WHEAT))
		assertEquals(1234.0, GardenFortune.tabFortune(tab, Crop.CARROT))
		assertEquals(null, GardenFortune.tabFortune(listOf("nothing"), Crop.WHEAT))
	}

	@Test
	fun contest() {
		assertEquals(48000L, GardenFortune.contestCrops(100.0, Crop.WHEAT))
		val crops = GardenFortune.contestCrops(500.0, Crop.CARROT)
		assertEquals(500.0, GardenProgress.ffNeeded(crops, Crop.CARROT), 0.01)
	}
}
