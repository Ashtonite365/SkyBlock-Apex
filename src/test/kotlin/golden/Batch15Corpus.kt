// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch15Sh {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]
	val pitches: List<Float> by lazy {
		val r = Random(1501)
		List(32) { it / 63f } + List(32) { it / 63f + 0.00009f } + List(32) { it / 63f - 0.00011f } + List(100) { r.nextFloat() * 0.6f } + listOf(Float.NaN, -0.2857143f, 0.4920635f, 0.5079365f)
	}
	val chumNames = listOf("Steve's Chum Bucket", "Ashton's Chum Bucket", "Ashton's Chumcap Bucket", "Ashtonite's Chum Bucket", "5/10 Chums", "10/10 Chums ", "Chum Bucket",
		"'s Chum Bucket", "", "Ashton's chum bucket", "x's Chumcap Bucket 5/10 Chums")
	val owns = listOf(null, "Ashton", "", "Steve")
	val shardLines = listOf("You caught a Cinderbat Shard!", "You caught an Azure Shard!", "LOOT SHARE You received 3x Lunar Moth Shards!", "You received x2 Bal's Shards!",
		"NAGA 5 Tadgang Shards", "SALT Salt Shard", "§aYou caught §9a Hideonleaf Shard§a!", "You caught a Shard!", "Random text", "You caught 99999999999999999999x Big Shards!",
		"You caught a Sea-Archer Shard!", "NAGA x12x Cod Shard!!", "abc You caught a Kraken Shard! def", "You caught a  Shard")
	val trophyIds = listOf("BLOBFISH_GOLD", "BLOBFISH_BRONZE", "BLOBFISH_DIAMOND", "SULPHUR_SKITTER_SILVER", "SULPHUR_SKITTER", "GOLD", "_GOLD", "UNKNOWN_DIAMOND", "BLOBFISH_PLATINUM", "", "VOLCANIC_STONEFISH_DIAMOND")
	val trophyTables: List<Map<String, List<Int>>> = listOf(
		emptyMap(), mapOf("BLOBFISH" to listOf(1, 2, 3, 4), "SULPHUR_SKITTER" to listOf(5, 6), "" to listOf(9, 9, 9, 9), "VOLCANIC_STONEFISH" to listOf(10, 20, 30, 40, 50)),
		mapOf("BLOBFISH" to emptyList(), "SULPHUR_SKITTER" to listOf(1, 1, 1, 1)))
	val bossTags: List<String> by lazy {
		val r = Random(1503)
		val names = listOf("Revenant Horror", "Atoned Horror IV", "Arachne", "Dragon", "Unknown Thing", "Storm", "Primal Fear", "Sea Emperor", "Kuudra")
		val hps = listOf("1.5M/3M❤", "500k❤", "1,234/2,000❤", "0/0❤", "12❤", "abc❤", "1.2.3/4❤", "3M/0❤", "", "❤", "5b/10B❤", "7/❤")
		List(300) { "§c${pick(r, listOf("", "☠ ", "[Lv99] "))}${pick(r, names)} ${pick(r, hps)}${pick(r, listOf("", " §e✯"))}" }
	}
	val combatLines = listOf("ENDER NODE! You found Enchanted Obsidian!", "ENDER NODE! You found 5x Enchanted Ender Pearl!", "ENDER NODE! You found 0x x!", "ENDER NODE! You found 99999999999999999999x x!",
		"ENDER NODE! You found !", "RARE DROP! (2x) Dragon Claw (+150% ✯ Magic Find)", "DROP! Summoning Eye", "PET DROP! Ender Dragon (★)", "LEGENDARY DROP! Aspect of the Dragons", " DROP! x",
		"EPICDROP! y", "You have spawned your Zealot soul! (500 Mana)", "You have spawned your  soul! (5 Mana)", "You have despawned your monster!", "You have despawned your monsters!",
		"The Seraph recalled your 3 summoned allies!", "The Seraph recalled your summoned allies!", "    ARACHNE DOWN!   ", "ARACHNE DOWN", "", "foo 5ᝐ bar", "x 12⁑", " 3| ", "no stack 5Ѫ", " 7⚶ 8҉")
	val tabs: List<List<String>> by lazy {
		val r = Random(1504)
		val pool = listOf(" Broodmother: Slain", "Broodmother: Alive!", "Broodmother: Unknown", "Broodmother: Imminent soon", " Volcano: INACTIVE", "Volcano: EXPLOSIVE now", "Volcano:", "Volcano:  X", "Area: Crimson Isle")
		List(150) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val npcLines = listOf("[NPC] Pablo: Are you available? I desperately need a Wilted Berberis today.", "[NPC] Pablo: ✆ Are you available? I desperately need an Ender Rose today.",
		"[NPC] Pablo: Bring me that Fire Flower as soon as you can!", " [NPC] Pablo: Could you bring me a Rose? ", "[NPC] Pablo: I really need an Orchid today, do you have one you could spare?",
		"[NPC] Pablo: Could you bring me a Rose!", "[NPC] Pablo: Bring me that  as soon as you can!", "[NPC] Sirih: Oink.", "[NPC] Sirih: ✆ Oink.", " [NPC] Sirih: Oink. ", "[NPC] Sirih:  Oink.", "")
	val patternCases = listOf("§aHello World" to "Hello (\\w+)", "Hello World" to "World", "abc" to "(?<x>a)(?<y>z)?bc", "" to ".*", "§§x" to "x", "Wo§rrld" to "World")
}
