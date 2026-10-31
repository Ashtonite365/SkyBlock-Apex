// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch36Superpairs {
	val titles = listOf("Superpairs (Beginner)", " Superpairs (High) ", "Superpairs (Grand)", "Superpairs (Supreme)", "Superpairs (Transcendent)", "Superpairs (Metaphysical)", "Superpairs (Unknown)",
		"Superpairs (Grand Master)", "Superpairs ()", "superpairs (High)", "Superpairs (High)x", "Chronomatron (High)", "")
	val names = listOf("?", " ? ", "Click a button!", "Click any button", "Click a second button!", "Click a secony button", "Click any button!", "Next button is instantly rewarded!", "Next button is instantly rewarded",
		"Click a seco button", "Enchanted Book", "", "??")
	val lores = listOf(listOf(), listOf("XX ULTRA-RARE BOOK! XX"), listOf("XX ULTRA-RARE BOOK! XX", "x"), listOf(" XX ULTRA-RARE BOOK! XX ", "", "  Feather Falling VI "), listOf("XX ULTRA-RARE BOOK! XX", "", "   "),
		listOf("ULTRA-RARE BOOK!", "", "Book"), listOf("XX ULTRA-RARE BOOK! XX", "a", "Mana Steal I", "z"))
	val chat = listOf("Bob has invited you to join their party!", "[MVP+] Alice has invited you to join their party! You have 60 seconds", "The party invite from Bob has expired.", "The party invite from [VIP] Alice has expired.",
		"The party invite from Alice has expired", "xx Carl has invited you to join their party!", "§9Dan has invited you to join their party!", "has invited you to join their party!", "The party invite from bob has expired.", "random", "")
	val commands = listOf("p accept", " P ACCEPT ", "party accept", "party  accept", "p accept Bob", "hub", "Hub", " hub ", "jerry", "warp hub", "", "garden", "is", "dungeon_hub")
	val warps = listOf("hub", "jerry", "garden", "dungeon_hub", "Is")

	val scripts: List<List<Pair<Map<Int, Boolean>, Int?>>> by lazy {
		val r = Random(3601)
		List(60) {
			List(r.nextInt(2, 30)) {
				val m = LinkedHashMap<Int, Boolean>()
				repeat(r.nextInt(0, 10)) { m[r.nextInt(12)] = r.nextInt(3) > 0 }
				m to listOf(null, 0, 0, 0, 1, 2, 5, -1)[r.nextInt(8)]
			}
		}
	}
}
