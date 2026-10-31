// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch49Lore {
	val r = Random(4949)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]

	val romans = listOf("", "I", "II", "IIII", "IV", "IX", "XL", "XC", "CD", "CM", "MMMCMXCIX", "MMMM", "MMMMM", "IIV", "VX", "LC", "DM", "IC", "XM", "VV", "LL", "DD",
		"i", "X I", "MCMXCIV", "CCCLXV", "XLII", "Q", "ⅠⅡ") + List(500) { (0 until r.nextInt(1, 9)).map { "IVXLCDMQ".random(r) }.joinToString("") }
	val ints = listOf(Int.MIN_VALUE, -5, -1, 0, 1, 3, 4, 9, 14, 40, 90, 400, 900, 1994, 3999, 4000, 10000, 123456) + List(200) { r.nextInt(0, 5000) }

	private val kw = listOf("Level", "Tier", "Chapter", "Skill", "Milestone", "Floor", "Collection", "Heart of the Mountain", "Heart of the  Forest", "level", "Floors", "Heart of the")
	private val rn = listOf("I", "IV", "VII", "XX", "IIII", "MMMM", "VX", "", "XIV", "i", "IIX", "M")
	val romanTexts = listOf("", "Floor VII", "Floor VIIa", "Floor VII.", "aFloor VII", "Level IV Tier IX", "Heart of the Mountain X", "Skill  V", "Collection XIV!",
		"Floor\u00a0VII", "§7Floor §cVII") + List(800) {
		(0 until r.nextInt(1, 4)).joinToString(listOf(" ", ", ", "", "§7").pick()) { "${kw.pick()}${listOf(" ", "", "  ", "_").pick()}${rn.pick()}${listOf("", " ", "!", "x", "1").pick()}" }
	}
	val hexTexts = listOf("", "#ff00ff", "#FF00FFa", "#ff00ff #00ff00", "#ff00f", "#gg0000", "x#123456 ", "##123456", "#1234567", "#123456_", "#ABCDEF.", "# 123456") +
		List(300) { (0 until r.nextInt(1, 4)).joinToString(" ") { "#" + (0 until r.nextInt(5, 8)).map { "0123456789abcdefABCDEFg".random(r) }.joinToString("") } }

	private val stats = listOf("Damage", "Health", "Defense", "True Defense", "Crit Chance", "Speed", "Intelligence", "Mining Fortune", "Rift Time", "Hearts", "Unknown",
		"Sea Creature Chance", " Strength", "Strength ", "speed", "Farming Fortune", "Pet Luck", "Magic Find", "Bonus Pest Chance")
	private val bonuses = listOf("+12", "-5", "+1.5%", "+20s", "12", "+1,000", "+", "+12%%", "+12x", "+.5", "+3 ", "+5s", "+%", "--1", "+0.5s ")
	val statLines = listOf("", ": +1", "Health: +100 HP", "Gear Score: 400", "Speed: +5 (+2)", "Damage: +12 Strength: +5", "Crit Damage: +50%", "§7Health: §a+100") +
		List(1500) { "${listOf("", "§7", " ", "x").pick()}${stats.pick()}: ${bonuses.pick()}${listOf("", " ", " (+5)", "x", "\n").pick()}" }

	val chimera = listOf("", "Copies your active pet's stats.", "§7Copies your active pet's stats. More", "Copies 100% of your active pet's stats", "Copies 80% of your active",
		"Copies 60% of your active pet's", "Copies 50% of your active", "Copies 20% of your active", "Copies 40% of your active", "xCopies 100% of your activex",
		"Copies 99999999999% of your active", "Copies % of your active", "Copies 100%  of your active", "Copies 100% of your active\nline") + List(100) { "Copies ${r.nextInt(0, 120)}% of your active pet's stats." }

	private val runPool = listOf("Floor VII", " Health: +100 ", "#ff8800", "Tier IV ", "Copies your active pet's stats.", "Speed: +5", "plain", "Earned by: Bob", "", "Level MMMM",
		"Strength: +12% ", "#00ff00 text", "Heart of the Mountain X")
	val lore: List<List<Pair<String, String?>>> = List(250) { List(r.nextInt(1, 4)) { runPool.pick() to listOf(null, "7", "c", "#123456", null).pick() } }
	val items = listOf("none", "memento", "memento-noname", "memento-earned", "other")

	val viewRecipes = listOf("", "viewrecipe", "viewrecipe ", "viewrecipe aspect of the end", "VIEWRECIPE aspect of the end 2", "viewrecipe ASPECT_OF_THE_END", "viewrecipe ASPECT_OF_THE_END 2",
		"viewrecipe 2", "viewrecipe  a   b  ", "  viewrecipe hyperion  ", "viewrecipe a 02", "viewrecipe a -1", "viewrecipe a 99999999999", "viewrecipex a", "recipe a", "viewrecipe ß straße",
		"viewrecipe a\tb", "viewrecipe ı", "viewrecipe A_B 3") + List(300) { "viewrecipe " + (0 until r.nextInt(1, 5)).joinToString(" ") { listOf("aspect", "of", "THE", "end", "2", "10", "x1", "Juju").pick() } }
	val storage = listOf("", "ec", "EC", "ec 1", "ec 9", "ec 10", "ec 0", "bp", "bp 0", "bp 18", "bp 19", "backpack 3", "enderchest", "enderchest 4", "  bp  ", "bp x", "bp -1", "bp 3 4", "ec  5",
		"warp hub", "e c") + List(300) { listOf("ec", "bp", "backpack", "enderchest", "ECx").pick() + listOf("", " ", " ${r.nextInt(-2, 21)}", " a").pick() }
	val cooldown = listOf("You may only use this command after 4s on the server!", "You may only use this command after 0s on the server!",
		"You may only use this command after 99999999999s on the server!", "You may only use this command after s on the server!", "Hello",
		" You may only use this command after 3s on the server! ", "You may only use this command after 2s on the server!x")

	val cmdSteps = List(700) {
		when (r.nextInt(5)) {
			0, 1 -> "cmd:" + (viewRecipes + storage + listOf("warp hub", "p list")).pick()
			2 -> "chat:" + cooldown.pick()
			3 -> "tick:" + r.nextInt(0, 120)
			else -> if (r.nextInt(8) == 0) "world" else "tick:1"
		}
	}
}
