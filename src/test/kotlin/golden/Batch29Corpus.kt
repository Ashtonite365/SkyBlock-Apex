// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch29Misc {
	private val bracketPool = listOf("DIAMOND (Top 2%): 1,234,567", " GOLD (Top 30%): 500", "PLATINUM (Top 5%): 9,999,999,999,999,999,999", "SILVER (Top 100%): 1", "BRONZE (Top 60%): ,",
		"DIAMOND (Top 2%): 7", "GOLD (Top 3%) 500", "§6GOLD (Top 30%): 500", "Wheat Contest", "", "random")
	val lores: List<List<String>> by lazy { val r = Random(2901); List(120) { List(r.nextInt(7)) { bracketPool[r.nextInt(bracketPool.size)] } } }
	private val tabPool = listOf("Jacob's Contest: 12m left", " Jacob's Contest: 3s left", "Jacob's Contest: 1h20m left", "Jacob's Contest: left", "○ Wheat 12m", "☘ Nether Wart",
		" ○ Cocoa Beans 1h5m left", "☘ Sugar Cane 3s xx", "○ 123", "Garden", "")
	val tabs: List<List<String>> by lazy { val r = Random(2902); List(150) { List(r.nextInt(9)) { tabPool[r.nextInt(tabPool.size)] } } }
	val summaries = listOf(0 to 0L, 1 to 999L, 1 to 1000L, 1234 to 61_000L, -5 to 5000L, Int.MAX_VALUE to Long.MAX_VALUE, 7 to -10_000L, 100 to 3_600_000L)
	val chat: List<String> by lazy {
		val names = listOf("White Gift", "Green Gift", "Red Gift", "Packed Ice", "Enchanted Ice", "Enchanted Packed Ice", "Ice Bait", "Glowy Chum Bait", "Glacial Fragment",
			"Glacial Talisman", "Frozen Bait", "Einary's Red Hoodie", "Nothing", "", "white gift")
		names.map { "FROZEN TREASURE! You found $it!" } + names.map { "FROZEN TREASURE! You found $it" } +
			listOf("COMPACT! You found an Enchanted Ice!", "COMPACT! You found an Enchanted Ice", "FROZEN TREASURE! You found ", "FROZEN TREASURE! You found !", "FROZEN TREASURE! You found White Gift!!", "")
	}
	val itemMaps: List<Map<String, Long>> by lazy {
		val r = Random(2903)
		val keys = listOf("COMPACT_ENCHANTED_ICE", "PACKED_ICE", "ENCHANTED_ICE", "ENCHANTED_PACKED_ICE", "WHITE_GIFT", "OTHER")
		List(60) { keys.filter { r.nextBoolean() }.associateWith { if (r.nextInt(10) == 0) Long.MAX_VALUE / 3 else r.nextLong(-5, 100_000) } }
	}
}
