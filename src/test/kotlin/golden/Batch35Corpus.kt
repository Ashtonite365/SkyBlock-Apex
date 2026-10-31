// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch35Garden {
	val times: List<Long> by lazy { val r = Random(3501); listOf(0L, Long.MIN_VALUE, Long.MAX_VALUE, 1_560_275_700_000L, 1_560_275_699_999L) + List(200) { r.nextLong(1_500_000_000_000L, 1_900_000_000_000L) } }
	val playing = listOf(listOf(), listOf("Now Playing: Pretty Fly"), listOf("  Now Playing: None  "), listOf("x", "Now Playing: DynaMITES", "Now Playing: Pray For Me"), listOf("Now Playing: "), listOf("Now Playing:  Cricket Choir"), listOf("§aNow Playing: Fly"))
	val logbook = listOf(listOf(), listOf("Times Visited: 1,000", "Offers Accepted: 250"), listOf("Offers Accepted: 5", "Times Visited: 0"), listOf("Times Visited: 10"), listOf(" Times Visited: ,", "Offers Accepted: 1"),
		listOf("Times Visited: 3", "Offers Accepted: 7", "Offers Accepted: 1"), listOf("Times Visited: 3 ", "Offers Accepted: 1"), listOf("Times Visited: 99999999999999999999999", "Offers Accepted: 1"))
	val screens = listOf("Crop Milestones", "Crop Upgrades", "Composter Upgrades", "Composter", "")
	val names = listOf("Wheat XV", "Melon Slice IX", "Sugar Cane 14", "Wheat", "Mushroom", "Cocoa Beans IV", "Nether Wart", "Upgrade X", "X", "Speed IIII", "Wheat ", "Pumpkin MMXX", "Carrot abc", "")
	val lores = listOf(listOf(), listOf("Current Tier: 5/9"), listOf("  Current Tier: 12/20 (max)"), listOf("Current Tier: /9", "Current Tier: 3/4"), listOf("Compost Available: 1,234"), listOf("Compost Available: 5 more"),
		listOf("Organic Matter Stored: 12,345.5/40k"), listOf("Fuel Stored: 1.2.3/10M", "Fuel Stored: 5/10"), listOf("Fuel: 50,000/100,000 "), listOf("a/b"))
	val slots = listOf(0, 13, 46, 52, 53)
	val gaps = listOf(Long.MIN_VALUE, -1L, 0L, 1L, 29_999L, 30_000L, 30_001L, Long.MAX_VALUE)
	val crops = listOf(0 to 0.0, 1 to 1.5, 10_000 to 1e15, 5 to Double.NaN, 3 to -2.7, 7 to Double.POSITIVE_INFINITY, 1 to 1e19)
}
