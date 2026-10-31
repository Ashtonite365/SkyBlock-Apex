// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch6Corpus {
	val rules = listOf("[a-zA-Z\\- ]+:>:max:c:0", "[a-zA-Z\\- ]+:=:max:6:0", "[a-zA-Z\\- ]+:=:max-1:5:0", "Smite:>:4:c:0", "Smite:<:4:a:31",
		"Sharpness:=:5:d:1", "Sharp.*:>:0:e:2", "Experience:>:3:b:4", "x:>:3", "x:>:3:g:0", "x:>>:3:c:0", "x:>:three:c:0", "x:>:3:c:zz", "(:>:3:c:0",
		"[a-z:>:3:c:0", "Smite:>:MAX:c:0", "Smite:>: max+1:c:0", "Smite:>:max-x:c:0", "Smite:>:max2:c:0", "", "a:b", "Smite:=:5:c:8:extra",
		"Smite:=:5:C:0", "Smite:=: 5:c:0", "Smite:=:-1:c:0", "Life Steal:>:2:4:16", "[A-Z][a-z]+ [A-Z][a-z]+:>:1:2:3", " Smite:>:1:c:0")

	val ruleSets: List<List<String>> = listOf(
		rules.take(3), listOf("Smite:>:4:c:0"), listOf("Sharp.*:>:0:e:2", "[a-zA-Z\\- ]+:>:max:c:0"), listOf("Smite:<:4:a:31", "Smite:>:4:c:0"),
		listOf("Life Steal:>:2:4:16", "[A-Z][a-z]+ [A-Z][a-z]+:>:1:2:3"), listOf("[a-zA-Z\\- ]+:>:0:c:0"), listOf(" Smite:>:1:c:0", "Smite:=:5:d:1"),
	)

	val maxLevels = mapOf("Sharpness" to 7, "Smite" to 7, "Experience" to 5, "Life Steal" to 5, "Ultimate Wise" to 5, "Protection" to 7)

	private val enchants = listOf("Sharpness", "Smite", "Experience", "Life Steal", "Ultimate Wise", "Protection", "Mystery", "First Strike", "Smite ")
	private val levels = listOf("I", "IV", "V", "VI", "VII", "X", "1", "5", "7", "10", "MMM", "", "iv")
	private val prefixes = listOf("§9", "§b", "§9§d§l", "§b§d§l", "§l§d§l", "§7", "", "§d§l", "§9§l")
	private val separators = listOf("§9, ", ", ", "§9,", " ", "", " §81,234", " §8,12,34", " §8", "§7, ")

	fun lines(): List<String> {
		val r = Random(71)
		val fixed = listOf("§9Sharpness VI§9, §9Smite V", "§9Experience V", "§9Mystery X", "§9Smite V", "§d§l§9Smite VII", "§9§d§lUltimate Wise V",
			"§9Smite V§9, §9Smite V§9, §9Smite V§9, §9Smite V§9, §9Smite V§9, §9Smite V§9, §9Smite V", "", "§9", "§9 Smite V", "§9Smite V §81,000")
		return fixed + List(600) {
			(0 until r.nextInt(1, 5)).joinToString("") {
				prefixes[r.nextInt(prefixes.size)] + enchants[r.nextInt(enchants.size)] + " " + levels[r.nextInt(levels.size)] + separators[r.nextInt(separators.size)]
			}
		}
	}

	val signLines = listOf("", " ", "1", "1,000", "1.5k*3", "10/0", "abc", "2^3", "1e3", "64s", "1k+", "  2k ", "-5", "1.5m", "0.005", "1/3", "3e/2")

	private val chMessages = listOf(
		"✦ You placed the Jade Crystal!", "✦ You placed the Fake Crystal!", "✦ You reclaimed the Amber Crystal!", "Keeper: You haven't placed the Topaz Crystal yet!",
		"[NPC] Keeper of Gold: You have already placed the Sapphire Crystal!", "  Amethyst Crystal", " Amethyst Crystal", "  Jade Crystal", "   Topaz Crystal",
		"  Ruby Crystal", "[NPC] Keeper of Diamond: Excellent! You have returned the Scavenged Diamond Axe to its rightful place!",
		"[NPC] Keeper of Lapis: You have already restored this Dwarf's Scavenged Lapis Sword!",
		"[NPC] Keeper of Gold: Talk to me when you have found a Scavenged Golden Hammer!",
		"[NPC] Keeper of Emerald: You found all of the items! Behold... the Jade Crystal!",
		"[NPC] Professor Robot: Thanks for bringing me the FTX 3070! Bring me 5 more components to fix the giant!",
		"[NPC] Professor Robot: Thanks for bringing me the Control Switch! Bring me one more component to fix the giant!",
		"[NPC] Professor Robot: That's not the final component! Bring me a Synthetic Heart to gain access to Automaton Prime's storage container!",
		"[NPC] Keeper of Diamond: Excellent! You have returned the Scavenged Diamond Axe to its rightful place! ", "hello", "",
		"[NPC] Keeper of 2: Excellent! You have returned the X to its rightful place!", "✦ You placed the Jade Crystal!\n",
	)

	fun chSessions(): List<List<String>> {
		val r = Random(72)
		return List(250) { List(r.nextInt(1, 7)) { chMessages[r.nextInt(chMessages.size)] } }
	}

	fun paneGrids(): List<List<Int>> {
		val r = Random(73)
		return List(300) { List(listOf(9, 18, 27, 36, 45, 54, 13)[r.nextInt(7)]) { if (r.nextInt(3) == 0) -1 else r.nextInt(3) } }
	}
}
