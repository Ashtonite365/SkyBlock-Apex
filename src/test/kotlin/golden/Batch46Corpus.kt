// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch46Pests {
	val r = Random(4646)
	val fixed = listOf(
		"GROSS! A Pest has appeared in Plot - 4!", "EWW! 2 Pests have spawned in Plot - 12!", "YUCK! 1 Pest has spawned in Plot - 7!", "ouch 3 Pests have spawned in Plot - Barn!",
		"Pests have spawned in Plot - 3!", "Pest has spawned in Plot - 99999999999!", "SuperPests have spawned in Plot - 1!", "x 2 Pests have spawned in Plot - 5! extra",
		"Pests have spawned in Plot -  5!", "", "RARE DROP! Mutant Nether Wart (+500% ✯ Pest Luck)", "PET DROP! Slug (Mythic) (+12% ✯ Magic Find)",
		"RARE DROP! Pest Shard x2 (+10 ✯ Magic Find) ", "RARE DROP! Pest Shard x1,000 (+10)", "RARE DROP! Thing x99999999999999999999 (+1)", "RARE DROP!  (x)",
		"You received 6x Enchanted Pumpkin for killing a Rat!", "  You received 1,024x Enchanted Carrot for killing an Earthworm!  ", "You received 0x Air for killing a  Mite !",
		"You received 99999999999999999999x Big for killing a Fly!", "You received 6x Enchanted Pumpkin for killing a Rat", "You received x Item for killing a Rat!",
		"You received 6x A for killing a B! for killing a C!",
	)
	private val words = listOf("Pests", "Pest", "have", "has", "spawned", "in", "Plot", "-", "4!", "RARE", "DROP!", "x3", "(+1%)", "You", "received", "6x", "for", "killing", "a", "an", "Rat!", "PET")
	val randomLines = List(2500) { (0 until r.nextInt(0, 12)).joinToString(" ") { words[r.nextInt(words.size)] } }
	private val tabPool = listOf(" Plots: 1, 4, 12", "Plots: 3", " Plots: a, 2,, 5 ,x", "  Plots: ", "Plots:", " Alive: 3", "Alive: 0 pests", " Alive: 12x", "Alive: 99999999999",
		" Alive: -1", "Pests:", "Spray: None", " Plots: 99999999999, 2", "Alive: 4 ", "  Alive: 7 (max)")
	val tabs: List<List<String>> = List(600) { List(r.nextInt(0, 6)) { tabPool[r.nextInt(tabPool.size)] } }
	val agos = listOf(Long.MIN_VALUE, -61_000L, -1L, 0L, 999L, 1000L, 59_999L, 60_000L, 61_500L, 3_600_000L, 123_456_789L, Long.MAX_VALUE)
	val prices = listOf(null, -1.0, 0.0, -0.0, 0.5, 1e9, Double.NaN, Double.POSITIVE_INFINITY)
}
