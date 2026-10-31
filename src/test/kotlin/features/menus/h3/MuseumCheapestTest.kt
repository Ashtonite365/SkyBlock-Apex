// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.features.museum.MuseumHelper.Missing
import dev.ashtonite365.apex.util.ItemId

class MuseumCheapestTest {
	private val bin = mapOf("ASPECT_OF_THE_END" to 120_000.0, "HYPERION" to 900_000_000.0, "SET_HELM" to 1_000.0, "SET_BOOTS" to 2_000.0)
	private val bid = mapOf("RARE_RELIC" to 3_000_000.0, "SET_CHEST" to 500.0)

	private val bazaar = setOf("ROTTEN_FLESH")
	private fun row(name: String, vararg ids: String) =
		Missing(name, ids.map { ItemId(it) }, "Combat", 0, { bin[it.skyblockId] }, { bid[it.skyblockId] }, { null }, { it.skyblockId in bazaar })

	@Test
	fun onlyListedItemsWithRealPriceAndLabel() {
		val rows = listOf(
			row("Aspect of the End", "ASPECT_OF_THE_END"),
			row("Cheap Npc Thing", "ROTTEN_FLESH"),
			row("Hyperion", "HYPERION"),
			row("Rare Relic", "RARE_RELIC"),
			row("Unknown", ),
			row("Set (one piece only bid)", "SET_HELM", "SET_CHEST", "SET_BOOTS"),
			row("Set missing a piece", "SET_HELM", "NOT_LISTED"),
		)
		val l = MuseumHelper.listedOnly(rows, MuseumHelper.Sort.CHEAPEST)

		assertEquals(listOf("Aspect of the End", "Hyperion", "Set missing a piece", "Set (one piece only bid)", "Rare Relic"), l.map { it.name })
		assertEquals(listOf(120_000.0, 900_000_000.0, null, null, null), l.map { it.price })
		assertEquals(listOf(false, false, true, true, true), l.map { it.estimate })
		assertEquals("Hyperion", MuseumHelper.listedOnly(rows, MuseumHelper.Sort.MOST_EXPENSIVE).first().name)
	}
}
