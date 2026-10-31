// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import kotlin.random.Random

object Batch14Misc {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]

	val petTags: List<String> by lazy {
		val r = Random(1401)
		val fixed = listOf("§8[§7Lv100§8] §6Golden Dragon", "§8[§7Lv200§8] §6Golden Dragon§c ✦", "§8[§7Lv1§8] §aBee", "§8[§7Lv99§8] §5Ender Dragon§d ✦",
			"[Lv100] Golden Dragon", "§8[§7Lv100§8]§6Golden Dragon", "§8[§7Lv§8] §6X", "§8[§7Lv99999999999§8] §6X", "", "§8[§7Lv100§8] §6Name-With-Dash", "§8[§7Lv0200§8] §6Bal")
		fixed + List(200) {
			val lvl = pick(r, listOf("1", "50", "100", "200", "199", "0", "300", "100a"))
			val star = pick(r, listOf("", "§c ✦", "§d ✦", " ✦", "§c✦"))
			val name = pick(r, listOf("Golden Dragon", "Bee", "Tiger 2", "Black Cat", "Ender_Dragon", "Rock!"))
			val col = pick(r, listOf("§6", "§a", "§5", "", "§§"))
			pick(r, listOf("§8[§7Lv$lvl§8] $col$name$star", "§8[§7Lv$lvl§8] $col$name$star ", " §8[§7Lv$lvl§8] $col$name$star"))
		}
	}
	val candyLores: List<List<String>> by lazy {
		val r = Random(1402)
		val pool = listOf("(3/10) Pet Candy Used", "  (10/10) Pet Candy Used ", "(0/10) Pet Candy Used", "(11/10) Pet Candy Used", "(3/9) Pet Candy Used", "Pet Candy Used",
			"(99999999999/10) Pet Candy Used", "(7/10) Pet Candy Used!", "Level 100", "", "(1/10) Pet Candy Used")
		List(200) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val scoreboards: List<List<String>> by lazy {
		val r = Random(1403)
		val pool = listOf("Server closing: 03:20", "§cServer closing: 1:05", "Server closing: 99999999999:00", "Server closing: :30", " Late Spring 7th", "Early Winter 31st",
			"Summer 2nd", "Autumn 3rd ", "Mid Spring 4th", "5:20am ☀", " 11:50pm ☽", "12:00", "Purse: 1,000", "Spring 22nd", "13:00pm")
		List(250) { List(r.nextInt(0, 6)) { pick(r, pool) } }
	}
	val kickLines = listOf("You were kicked while joining that server!", "There was a problem joining SkyBlock, try again in a moment!", "You were kicked while joining that server", "", " You were kicked while joining that server!")
	val moves: List<Triple<Double, Double, Int>> by lazy {
		val r = Random(1404)
		listOf(Triple(0.0, 0.0, 1), Triple(3.0, 4.0, 0), Triple(-0.0, 0.0, 0), Triple(Double.NaN, 1.0, 2), Triple(1e200, 1e200, 1), Triple(0.3, -0.4, -2)) +
			List(100) { Triple(r.nextDouble(-2.0, 2.0), r.nextDouble(-2.0, 2.0), r.nextInt(1, 40)) }
	}
	val histories: List<Triple<Map<String, Long>, Long, Long>> by lazy {
		val r = Random(1405)
		List(150) {
			val now = 1_700_000_000_000L
			val m = LinkedHashMap<String, Long>()
			repeat(r.nextInt(0, 8)) { m["mini${r.nextInt(0, 12)}${pick(r, listOf("A", "B", "C"))}"] = now - pick(r, listOf(0L, 1L, 1000L, 60_000L, 600_000L, 3_600_000L, -5000L, 600_000L)) + r.nextLong(0, 3) * 1000 }
			Triple(m, now, pick(r, listOf(0L, 60_000L, 600_000L, 3_600_000L, -1L)))
		}
	}
	val gainPairs: List<Pair<Map<String, Long>, Map<String, Long>>> by lazy {
		val r = Random(1406)
		val ids = listOf("ENCHANTED_SUGAR", "CARROT_ITEM", "MAGMA_FISH", "SPONGE", "LILY_PAD")
		fun m() = LinkedHashMap<String, Long>().also { m -> repeat(r.nextInt(0, 5)) { m[pick(r, ids)] = pick(r, listOf(0L, 1L, 64L, 640L, -3L, Long.MAX_VALUE, Long.MIN_VALUE)) } }
		List(200) { m() to m() }
	}
	val npcLores: List<List<String>> by lazy {
		val r = Random(1407)
		val pool = listOf("Click to set the location of this NPC!", " Click to set the location of this NPC! ", "Click to spawn at your location!", "location!", "Location: [1, 2, 3]",
			" Location: [-10, 70, 5] ", "Location: [1, 2]", "Location: [a, 2, 3]", "Villager", "", "Click to set the location of this NPC")
		List(250) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val peltLines = listOf("Your mob died randomly, you are rewarded 5 pelts.", " Killing the animal rewarded you 12 pelts. ", "Killing the animal rewarded you 0 pelts.",
		"Killing the animal rewarded you 99999999999 pelts.", "Your mob died randomly, you are rewarded pelts.", "Killing the animal rewarded you 3 pelts", "", "You are rewarded 5 pelts.",
		"Your mob died randomly, you are rewarded 1 pelts.")
	val texts: List<Component> by lazy {
		val r = Random(1408)
		val words = listOf("hello", "world", "Golden Dragon", "foo bar foo", "", "ab", "§cred", "Lv100")
		val styles = listOf(Style.EMPTY, Style.EMPTY.withColor(ChatFormatting.RED), Style.EMPTY.withBold(true), Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(true))
		List(200) {
			val root = Component.literal(pick(r, words)).withStyle(pick(r, styles))
			repeat(r.nextInt(0, 4)) { root.append(Component.literal(pick(r, words)).withStyle(pick(r, styles))) }
			root
		}
	}
	val replacePairs = listOf("foo" to "baz", "o" to "0", "Golden Dragon" to "GD", "hello" to "hello", "" to "x", "zzz" to "y", "ld" to "")
}
