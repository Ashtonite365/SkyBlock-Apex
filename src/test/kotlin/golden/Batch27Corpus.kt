// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch27Sh {
	val gardenChat: List<String> by lazy {
		val r = Random(2701)
		val pool = listOf("+12 Garden Experience", "  +1,234.5 Garden Experience", "+1.2.3 Garden Experience", "+, Garden Experience", "+12 Garden Experience!", "§a+15 Garden Experience",
			"OVERFLOW! Your Euclid's Wheat Hoe has just dropped a Tool Exp Capsule!", "OVERFLOW! Your Fungi Cutter has just dropped a Tool Exp Capsule!",
			"WOAH! You caught a case of the CROP FEVER for 60 seconds!", "GONE! Your CROP FEVER has been cured!", "RARE! You dropped 3x Enchanted Wheat!", "UNCOMMON! You dropped 12x Wheat!",
			"JACKPOT! You dropped 1x Enchanted Hay Bale!", "RARE! You dropped 99999999999999999999x Wheat!", "RARE CROP! Cropie", "VERY RARE CROP! Squash (+50.0)", "RARE CROP! Fermento (automatically donated)",
			"RARE CROP! Unknown Thing (+1.5) (automatically donated)", "RARE CROP! ", "random line", "")
		List(400) { pool[r.nextInt(pool.size)] }
	}
	val lookup: (String) -> String? = { n -> if (n.startsWith("Unknown")) null else n.uppercase().replace(' ', '_') }
	val outbid = listOf("[Auction] Bob outbid you by 1,000 coins for Hyperion bidding 1B coins!", "[Auction] outbid you by x", "[Auction]  outbid you by ", "Auction] Bob outbid you by 5", "§6[Auction] §aBob §eoutbid you by §61 coins",
		"[Auction] Bob outbid you", "")
	val donated = listOf("Items Donated: 100%", "Items Donated: 99%", "§7Items Donated: §a100%", " Items Donated: 100%", "Items Donated: 100%!")
	val ids = listOf(null, "NEW_BOTTLE_OF_JYRRE", "DISCRITE", "DARK_CACAO_TRUFFLE", "", "new_bottle_of_jyrre")
	val held = listOf(Int.MIN_VALUE, -1, 0, 1, 1_079_999, 1_080_000, 1_080_001, Int.MAX_VALUE)
}
