// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch3aCorpus {
	private val watchdogVocab = listOf("[WATCHDOG ANNOUNCEMENT]", "", "   ", "Watchdog has banned 4,012 players in the last 7 days.",
		"Staff have banned an additional 1,200 in the last 7 days.", "Blacklisted modifications are a bannable offense!", "hello",
		" [WATCHDOG ANNOUNCEMENT]", "[WATCHDOG ANNOUNCEMENT] ", "[watchdog announcement]", "\t")

	private val bar = "▬"
	private val arachneVocab = listOf(
		"Arachne used Venom Shot on you hitting you for 1,234.5 damage and infecting you with venom.",
		"Arachne's Keeper used Venom Shot on you hitting you for 50 damage and infecting you with venom.",
		"Arachne's Brood used Venom Shot on you hitting you for 7 damage and infecting you with venom.",
		"Arachne's Pet used Venom Shot on you hitting you for 7 damage and infecting you with venom.",
		"Arachne used Venom Shot on you hitting you for 1e5 damage and infecting you with venom.",
		"☄ Bob placed an Arachne's Calling! Something is awakening! (4/4)", "☄ Bob placed an Arachne's Calling!",
		"☄ [MVP+] Bob placed an Arachne Crystal! Something is awakening!", "☄ Bob placed an Arachne Crystal! Something is awakening",
		"[BOSS] Arachne: The Era of Spiders begins now.", "[BOSS] Arachne: Ahhhh...A Calling...", "[BOSS] Arachne: hi",
		bar.repeat(21), bar.repeat(20), "  " + bar.repeat(30) + "  ", bar.repeat(25) + "x", "ARACHNE DOWN!", " ARACHNE DOWN! ",
		"                 ARACHNE DOWN!", "arachne down!", "Bob: hi", "", "   ", "  Arachne used Venom Shot on you hitting you for 9 damage and infecting you with venom.  ")

	private fun sequences(tag: String, vocab: List<String>, seed: Int, n: Int): List<String> {
		val r = Random(seed)
		return List(n) { i -> "$tag$i:" + List(r.nextInt(1, 40)) { vocab[r.nextInt(vocab.size)] }.joinToString("⏎") }
	}
	val watchdog get() = sequences("w", watchdogVocab, 31, 400)
	val arachne get() = sequences("a", arachneVocab, 32, 400)
	fun lines(key: String) = key.substringAfter(':').split("⏎")

	fun actionBars(): List<String> {
		val r = Random(33)
		val pre = listOf("", "§c1000/1000❤     ", "     ", "\uE001", "§7", "§a", "x", "Room: ")
		val nums = listOf("0", "1", "3", "7", "10", "20", "21", "1,234", "03", "99", "5")
		val seps = listOf("/", " / ", "  /", "/ ", "\u00A0/\u00A0", "\\")
		val words = listOf("Secrets", "Secret", "secrets", "SECRETS", "Secretsx", "Secrets!", " Secrets", "  Secrets", "\u00A0Secrets", "Secr")
		val post = listOf("", "     §b500/500✎ Mana", " ", "§r")
		val fixed = listOf("No Secrets", "NoSecrets", "§7No Secrets", "xNo Secrets", "     No Secrets", "no secrets", "No Secretsy",
			"\u00A0No Secrets", "\uE000No Secrets", "", "nothing", "3/7", "§e3§7/§e7 Secrets", "3 §7/ 7 Secrets")
		return fixed + List(3000) { pre[r.nextInt(pre.size)] + nums[r.nextInt(nums.size)] + seps[r.nextInt(seps.size)] + nums[r.nextInt(nums.size)] + words[r.nextInt(words.size)] + post[r.nextInt(post.size)] }
	}

	fun tabLists(): List<List<String>> {
		val r = Random(34)
		val v = listOf(" Secrets Found: §b23", "Secrets Found: 38.5%", " §7Secrets Found: §61,234", "Secrets Found: 0%", "Secrets Found: 100.25 %",
			"Secrets Found: 12 ", "Secrets Found: abc", "secrets found: 5", "Secrets Found: 5.%", "Crypts: 5", "Secrets Found:\u00A07",
			" Secrets Found: §60.0%", "Secrets Found: 3", "Secrets Found: 45.7%", "Secrets Found: 99.99%", "Secrets Found: 0", "§b§lDungeon Stats",
			"Secrets Found: 1,000.5%", "   Secrets Found:   17   ", "Secrets Found: 12.%")
		return List(1500) { List(r.nextInt(0, 5)) { v[r.nextInt(v.size)] } }
	}
	val fractions = listOf(0.0, 0.2, 0.5, 0.7, 1.0)

	val loreLines = listOf("§6§lLEGENDARY DUNGEON SWORD", "§5§lEPIC DUNGEON ITEM", "DUNGEON", "DUNGEON ITEM", "DUNGEON ITEMS", "dungeon", "RARE DUNGEON BOOTS",
		"DUNGEONITEM", "DUNGEON  ITEM", "XDUNGEONX", "§7Gear Score: §d300", "", "DUNGEON ITEM DUNGEON", "SPECIAL DUNGEON", "COMMON DUNGEON ITEM")
	val coreIds = listOf("DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE", "POTION", "POTION_HEALING", "POTIONX", "XPOTION",
		"defuse_kit", "ENCHANTED_BOOK", "", "SPIRIT_LEAP")
	val hoverNames = listOf("Healing VIII Splash Potion", "§cHealing VIII Splash Potion", "Healing VII Splash Potion", "Healing VIII Splash Potion x2", "Defuse Kit", "")
}
