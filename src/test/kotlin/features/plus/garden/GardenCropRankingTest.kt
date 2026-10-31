// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenCropRanking as R
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop

class GardenCropRankingTest {
	@Test
	fun ranking() {
		assertEquals(144000.0, R.cropsPerHour(Crop.WHEAT, 20.0, 100.0))
		val money = R.moneyRanking(20.0, 0.0) { if (it == Crop.WHEAT) 6.0 else if (it == Crop.MELON) 2.0 else null }
		assertEquals(listOf(Crop.MELON, Crop.WHEAT), money.map { it.first })
		val time = R.timeRanking(20.0, 0.0) { if (it == Crop.WHEAT) 72000L else if (it == Crop.CACTUS) 72000L else null }
		assertEquals(Crop.CACTUS, time.first().first)
		assertEquals(1.0, time.last().second, 1e-9)
		assertEquals("1h 30m", R.formatHours(1.5))
	}
}
