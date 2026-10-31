// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenAnitaFortune
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import kotlin.random.Random

object Batch13Garden {
	val bpsList = listOf(20.0, 0.5, 19.99, 0.0, -1.0)
	val ffList = listOf(0.0, 150.0, 1234.5, -100.0, Double.NaN)
	private val priceVals = listOf(null, 0.0, -3.0, 1.0, 2.5, 7.25, 1e6, Double.NaN, 3.0, 3.0)
	val priceMaps: List<Map<Crop, Double?>> by lazy { val r = Random(1301); List(150) { Crop.entries.associateWith { priceVals.random(r) } } }
	private val leftVals = listOf(null, 0L, -50L, 1L, 1000L, 123_456L, 99_999_999L, 5000L, 5000L)
	val leftMaps: List<Map<Crop, Long?>> by lazy { val r = Random(1302); List(150) { Crop.entries.associateWith { leftVals.random(r) } } }
	val hours = listOf(0.0, 0.0166, 0.999, 1.0, 1.0001, 2.5, 59.99, 100.123, -0.5, -2.0, Double.NaN, Double.POSITIVE_INFINITY, 1e15, 0.5 / 60)

	private val anitaPool = listOf("You have: +12☘ Farming Fortune", " You have: +0 Farming Fortune ", "You have: +40☘ Farming Fortune", "You have: +7 Farming Fortune",
		"You have: +99999999999 Farming Fortune", "You have +4 Farming Fortune", "Jacob's Ticket x50", " Jacob's Ticket x1,250 ", "Jacob's Ticket x12.5", "Jacob's Ticket x1.2.3",
		"Jacob's Ticket x", "Gold medal x2", "Cost", "", "Click to trade!", "Extra Farming Fortune", "You have: +16☘ Farming Fortune")
	val anitaLores: List<List<String>> by lazy { val r = Random(1303); List(300) { List(r.nextInt(0, 7)) { anitaPool.random(r) } } }
	val priceTables: List<Map<Int, GardenAnitaFortune.Price>> by lazy {
		val r = Random(1304)
		List(60) { t ->
			val levels = r.nextInt(0, 16)
			buildMap { for (l in 1..levels) if (r.nextInt(8) > 0 || t % 5 == 0) put(l, GardenAnitaFortune.Price(r.nextInt(0, 5), listOf(0, 10, 25, 50, 100, 1_000_000_000).random(r))) }
		}
	}
	val breakSets: List<Pair<List<Long>, Long>> by lazy {
		val r = Random(1305)
		List(200) { val now = 1_700_000_000_000L + r.nextLong(0, 10_000); List(r.nextInt(0, 40)) { now - r.nextLong(-1000, 9000) }.sorted() to now }
	}
	val windows = listOf(5_000L, 1_000L, 1L)
}
