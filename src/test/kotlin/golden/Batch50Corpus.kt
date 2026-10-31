// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch50Misc {
	val r = Random(5050)
	private fun <T> List<T>.pick() = this[r.nextInt(size)]

	private val digits = listOf("0", "1", "12", "1,234", "1.5", "999,999", "12.3.4", ",", ".", "1,", "007")
	private val sfx = listOf("", "k", "K", "m", "M", "b", "B", "kk", "x", "t")
	private val sym = listOf("", "✧", "✯", "❤", "♞", "☄", "⚔", "✷", "✧✧", "❤⚔", "♥", "★")
	val splashes = listOf("", " ", "1", "✧1✧", "✯12,345✯", "12 345", " 1k ", "1.2M❤", "abc", "1a", "✧", "✧✧1", "-1", "1 k", "1,0,0", "１２", "1\u00a02",
		"§c1", "1kk", "✷1✷", "1⚔⚔⚔", "12.5b✧") + List(700) {
		listOf("", "✧", "✯", "", "x", " ").pick() + digits.pick() + listOf("", " ", "").pick() + sfx.pick() + sym.pick() + listOf("", "", " ", "✧").pick()
	}

	val names = listOf("", "Zombie", "Revenant Horror", "§cZombie", "zombie", "Ender Dragon", "Party Member", "ZOMBIE VILLAGER", "a|b", "x,y")
	val nameLists = listOf("", " ", "|", ",", "zombie", "ZOMBIE", "dragon|zombie", " zombie , dragon ", "x|", "|y", "a|b", "villager,", "ender drag", ",,,", "member|party", "§c")

	private val oldVals = listOf("true", "false", "\"true\"", "1", "null", "\"yes\"", "{}", "[]", "\"\"")
	private val fireVals = listOf("\"normal\"", "\"hidden\"", "\"low-on-fire\"", "1", "null")
	val migrations = listOf("{}", "{\"hide-fire-overlay\":true}", "{\"hide-fire-overlay\":false}", "{\"hide-fire-overlay\":\"true\"}",
		"{\"fire-overlay\":\"normal\",\"hide-fire-overlay\":true}", "{\"a\":1,\"hide-fire-overlay\":true,\"b\":2}") + List(250) {
		val parts = mutableListOf<String>()
		if (r.nextBoolean()) parts += "\"a\":${r.nextInt(5)}"
		if (r.nextInt(4) != 0) parts += "\"hide-fire-overlay\":${oldVals.pick()}"
		if (r.nextInt(3) == 0) parts += "\"fire-overlay\":${fireVals.pick()}"
		if (r.nextBoolean()) parts += "\"z\":\"q\""
		parts.shuffle(r)
		"{" + parts.joinToString(",") + "}"
	}

	val distances = listOf(-1.0, 0.0, 1.0, 3.99, 4.0, 4.01, 63.0, 64.0, 64.0001, 1599.99, 1600.0, 1600.01, 1e9, Double.NaN, Double.POSITIVE_INFINITY) + List(60) { r.nextDouble(0.0, 3000.0) }
	val farDists = listOf(-3, 0, 1, 8, 40, 128, 46341, 50000)

	val soundPaths = listOf("entity.firework_rocket.blast", "entity.firework_rocket.blast_far", "entity.creaking.death", "entity.firework_rocket.large_blast",
		"block.note_block.pling", "entity.creaking.hurt", "Entity.firework_rocket.blast")
	val volumes = listOf(20f, 19.99f, 20.0001f, 0f, 1f, -20f, 40f)
	val tabs = listOf(listOf(), listOf("Cooldown: AVAILABLE"), listOf(" Cooldown: AVAILABLE"), listOf("  Cooldown: AVAILABLE"), listOf("Cooldown: AVAILABLE "),
		listOf("x", "Beacon:", " Cooldown: 5m"), listOf("cooldown: available"), listOf("Cooldown: AVAILABLEx"), listOf("a", "b", " Cooldown: AVAILABLE"))
	val forageChat = listOf("", "You stick your hand into the honeyhive and feel around...", "  You stick your hand into the honeyhive and feel around...  ",
		"You stick your hand into the honeyhive and feel around..", "QUEEN BEE! The Honeyhive instantly refilled with loot!", "QUEEN BEE! The Honeyhive instantly refilled with loot",
		"You must complete the Foraging Tutorial Quest to use this!", "You must complete the Into the Woods Quest to use this!", "You must complete the A Helping Hand Quest to use this!",
		"You must complete the The Campfire Cult Quest to use this!", "You must complete the The Rebuild Quest to use this!", "You must complete the Rebuild Quest to use this!",
		"You must complete the  Quest to use this!", "You must complete the foraging tutorial Quest to use this!", " You must complete the Into the Woods Quest to use this! ",
		"You must complete the Into the Woods Quest to use this!!", "§aYou must complete the Into the Woods Quest to use this!")
	val questNames = listOf("Foraging Tutorial", "Into the Woods", "A Helping Hand", "The Campfire Cult", "The Rebuild", "Rebuild", "", "into the woods", "Into the Woods ", "X")
	val questMsgs = forageChat + List(200) { "${listOf("You must complete the ", "You must complete the", "you must complete the ", "").pick()}${questNames.pick()}${listOf(" Quest to use this!", " Quest to use this", " quest to use this!", "Quest to use this!").pick()}" }

	val kickChat = listOf("", "You were kicked while joining that server!", "There was a problem joining SkyBlock, try again in a moment!",
		"  You were kicked while joining that server!  ", "You were kicked while joining that server", "§cYou were kicked while joining that server!", "Hello")
	val restartBoards = listOf(listOf(), listOf("Server closing: 01:00"), listOf("Server closing: 00:30"), listOf("Server closing: 00:31"), listOf("Server closing: 02:00"),
		listOf("Server closing: 00:05"), listOf("x", "Server closing: 03:17"), listOf("Server closing: 00:00"), listOf("Server closing: 1:00"))
	val servers = listOf("mini1A", "mini2B", "mega7", "mini1A", "mini3C", "mini2B", "mini1A", "m9", "mega7", "m9")
}
