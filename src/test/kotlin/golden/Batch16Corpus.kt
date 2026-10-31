// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch16Sh {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]
	val toolIds = listOf(null, "", "TREECAPITATOR_AXE", "JUNGLE_AXE", "JUNGLE_AXE_2", "SWEET_AXE", "DIAMOND_PICKAXE", "PICKAXE_AXE", "ROOKIE_AXE", "AXE", "_AXE",
		"FISHING_ROD", "ROD_OF_THE_SEA", "CHALLENGE_ROD", "GIANT_FISHING_ROD", "SHREDDER", "MAGMA_LINE_ROD", "ROD", "_ROD_X", "STARTER_LAVA_ROD", "jungle_axe")
	val specs = listOf("", "a=>b", "a=>b;c=>d", "=>x", "a=>", ";;a=>b;", "foo=>bar=>baz", "x;y=>z", "Hello=>Bye;bye=>hi", " sp => ce ", "§c=>§a", "a=>b;a=>c", "ab=>a")
	val texts = listOf("", "a", "abc abc", "Hello hello HELLO", "§cRed sp text", "aaa", "foo bar", "ABab")
	val shorts = listOf("1,234", "12.5k", "3M", "2b", "2B", "k", "", " 5 ", "1.2.3", "NaN", "-4k", "1e3", "1e3k", "Infinity", "0x10", "5kk", "12,5", "m", "7 m", "1_000")
	val progress: List<String> by lazy {
		val r = Random(1601)
		val fixed = listOf("(1,234/2,400) Combat XP", "12k/15k Kills", "(0/0) Combat XP", "(5/3) x", "1/2", "(1/2)Combat", "(1.5k/2M) XP", "(a/2) x", " (10/20) y ", "(1,,2/3) z")
		fixed + List(100) { "${pick(r, listOf("(", ""))}${pick(r, shorts.take(6))}/${pick(r, shorts.take(8))}${pick(r, listOf(")", ""))} ${pick(r, listOf("Combat XP", "Kills", ""))}" }
	}
	val kills = listOf(Triple(0.0, 100.0, 10.0), Triple(95.0, 100.0, 10.0), Triple(100.0, 100.0, 7.0), Triple(150.0, 100.0, 7.0), Triple(0.0, 100.0, 0.0), Triple(0.0, 100.0, -1.0),
		Triple(0.0, 1e12, 0.001), Triple(0.0, Double.NaN, 1.0), Triple(1.0, 2.0, Double.NaN), Triple(0.0, 3.0, 1e-320))
	val lores: List<List<String>> by lazy {
		val r = Random(1602)
		val pool = listOf("Only inside The Rift!", "Doesn't exist here!", " Only inside The Rift!", "You need 64 more Enchanted Cobblestone.", "  You need 5 more Wheat.", "You need 99999999999 more X.",
			"You need more X.", "You need 3 more .", "Upgrade", "", "You need 1 more Enchanted Pork")
		List(150) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val mobTags = listOf("[Lv7] Graveyard Zombie 120/120❤", "[Lv1] Zombie 100/100❤", "[Lv100] ☠ Voidling Extremist 1.2M/8M❤", "[Lv7] Revenant Horror 1k/2k❤", "[Lv5] Wolf 5/5",
		" [Lv12] Pack Spirit 3,000/3,000❤ ", "[Lv90] Corrupted Zombie Villager 1B/2B❤", "[Lv] Zombie 1/1❤", "[Lv9] Smoldering Blaze 9k/9k❤", "[Lv9] Old Wolf 1m/1m❤", "Zombie")
	val pillars = listOf("5s 8 hits", " 12s 8 hits ", "s 8 hits", "99999999999s 8 hits", "5s 7 hits", "05s 8 hits")
	val shields = listOf("ASHEN ♨", "Spirit", "CRYSTAL AURIC", "§bCRYSTAL", "", "SPIRITASHEN", "Twilight")
	val twin = listOf("TWINCLAWS 1.5s", "§c§lTWINCLAWS 0s §r", "TWINCLAWS s", "twinclaws 1s", "xTWINCLAWS 3,0s y")
	val rng = listOf("RNG Meter - 1,234 Stored XP", "   RNG Meter - x Stored XP", "    RNG Meter - x Stored XP", "RNG Meter -  Stored XP", "RNG Meter - 5 Stored XP!")
	val titles = listOf(null, "Inferno Minion I", "Inferno Minion XII", "Inferno Minion 11", "Inferno Minion", "Inferno Minion IIIII", "Wheat Minion IV", "Cobblestone Minion 12",
		" Minion I", "Minion I", "Inferno Minion i")
	val fuels = listOf("Inferno Minion Fuel", "Inferno Fuel Block", "Inferno Minion Fuel (Crude Gabagool)", "inferno fuel", "Enchanted Lava Bucket", "", "Inferno")
	val piggy = listOf("You died and your piggy bank cracked!", "You died, lost 1,234.5 coins and your piggy bank broke!", "You died, lost  coins and your piggy bank cracked!",
		"You died and your piggy bank shattered!", " You died and your piggy bank broke!", "You died, lost 5 coins and your piggy bank broke")
	val bottleSets: List<List<String>> by lazy {
		val r = Random(1603)
		val pool = listOf("THUNDER_IN_A_BOTTLE", "STORM_IN_A_BOTTLE", "HURRICANE_IN_A_BOTTLE", "THUNDER_IN_A_BOTTLE_EMPTY", "STORM_IN_A_BOTTLE_EMPTY", "DIRT", "ROD_OF_THE_SEA")
		List(120) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val deaths = listOf(" ☠ Steve was killed by Zombie.", "☠ You died.", "☠ Ashton_365 fell into the void.", "☠ Steve", "☠  Steve x", "☠ Ste-ve died", "x ☠ Steve died", "☠ You  died")
	val actionBars = listOf("§c100/100❤     §b+5 SkyBlock XP §7(Fishing)§b (12/100)     §a50❈ Defense", "§b+5 SkyBlock XP §7(Fishing)§b (12/100)", "§b+5 SkyBlock XP (Fishing) (12/100)",
		"§b+10 SkyBlock XP §e(Some (nested))§b (1/2)", "", "§b+5 SkyBlock XP §7(A)§b (1/2) §b+6 SkyBlock XP §7(B)§b (3/4)")
}
