// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch45Mining {
	val r = Random(4545)
	val fixed = listOf(
		"RARE DROP! Sorrow (+250% ✯ Magic Find)", "RARE DROP! Volta (+12 ✯ Magic Find)", "  RARE DROP! Plasma (+0% ✯ Magic Find)  ", "RARE DROP! X (+99999999999% ✯ Magic Find)",
		"RARE DROP! (+5% ✯ Magic Find)", "RARE DROP! A (B) (+5% ✯ Magic Find)", "RARE DROP! Sorrow (250% ✯ Magic Find)",
		"Your Kill Combo has expired! You reached a 1,234 Kill Combo!", "Your Kill Combo has expired! You reached a 12.5 Kill Combo!", "Your Kill Combo has expired! You reached a ,,. Kill Combo!",
		"Your Kill Combo has expired! You reached a 99,999,999,999 Kill Combo!", "The ghost's death materialized 1,000,000 coins from the mists!",
		"LAPIS CORPSE LOOT!", "  UMBER CORPSE LOOT! ", "VANGUARD CORPSE LOOT!", "lapis CORPSE LOOT!", "CORPSE LOOT!", "TUNGSTEN_X CORPSE LOOT!",
		"LUCKY! Your Resourceful perk saved your key from being consumed!", "EXCAVATOR! You found a Suspicious Scrap!", " A Golden Goblin has spawned! ", "A Diamond Goblin has spawned!",
		"A Diamond Goblin has spawned!!", "CRYSTAL NUCLEUS LOOT BUNDLE", "  CRYSTAL NUCLEUS LOOT BUNDLE ", "EXCAVATION COMPLETE ", "EXCAVATION COMPLETE!", "REWARDS", "  REWARDS",
		"▬▬▬▬▬▬▬▬▬▬", "  ▬▬▬  ", "", "   ", "  Jasper Crystal", "  Jasper Crystal x3", "  +1,200 Fossil Dust", "  1,200x Fossil Dust", "  +5 Gemstone Powder x2",
		"  Item x1,000,000,000,000", "  +99999999999 Dust", "   ▬▬▬", "  Name x", " One space", "\tTab Item x2", "  x5", "  5", "  +5", "  +5x Thing", "  Prehistoric Egg x0",
	)
	private val words = listOf("Jasper", "Crystal", "Fossil", "Dust", "x3", "+1,200", "Bob", "▬", "REWARDS", "x", "12", "Gemstone")
	val randomLines: List<String> = List(3000) {
		val pad = listOf("", " ", "  ", "   ", "\t ")[r.nextInt(5)]
		pad + (0 until r.nextInt(0, 5)).joinToString(" ") { words[r.nextInt(words.size)] } + if (r.nextInt(6) == 0) " x${r.nextInt(0, 2000)}" else ""
	}

	val streams: List<List<String>> = List(150) {
		List(r.nextInt(5, 40)) {
			when (r.nextInt(10)) {
				0 -> listOf("CRYSTAL NUCLEUS LOOT BUNDLE", "EXCAVATION COMPLETE ", "LAPIS CORPSE LOOT!")[r.nextInt(3)]
				1 -> "▬".repeat(r.nextInt(1, 30)).let { if (r.nextBoolean()) "  $it" else it }
				2 -> fixed[r.nextInt(fixed.size)]
				else -> randomLines[r.nextInt(randomLines.size)]
			}
		}
	}
}
