// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch8Corpus {

	data class C(val path: String, val name: String, val count: Int, val empty: Boolean = false)

	sealed interface Ev
	data class Tick(val cells: List<C>) : Ev
	data class Click(val slot: Int) : Ev

	class Session(val superpairs: Boolean, val showNext: Boolean, val numbers: Boolean, val reveal: Boolean, val events: List<Ev>)

	private val air = C("air", "Air", 0, true)
	private val dyes = listOf("red_dye", "lime_dye", "ink_sac", "cocoa_beans", "lapis_lazuli", "bone_meal", "purple_dye", "cyan_dye")
	private val fillers = listOf("black_stained_glass_pane", "gray_stained_glass", "stone", "paper", "red_terracotta")

	val titles = listOf("Ultrasequencer (Metaphysical)", "Ultrasequencer", "Superpairs (Transcendent)", "Superpairs",
		"Superpairs Stakes", "Ultrasequencer Stakes", "Chronomatron (High)", "Experimentation Table", "", "superpairs",
		" Superpairs", "Ultrasequencer ➜ Stakes", "Superpairs Rewards", "Stakes Superpairs")

	private fun sequencer(r: Random, i: Int): Session {
		val size = if (i % 23 == 0) r.nextInt(0, 6) else 54
		val events = mutableListOf<Ev>()
		var base = List(size) { if (r.nextInt(4) == 0) C(fillers.random(r), "", 1) else air }
		fun withStatus(cells: List<C>, status: C?) = if (status == null || cells.size < 5) cells else cells.toMutableList().also { it[it.size - 5] = status }
		repeat(r.nextInt(1, 4)) {
			val n = r.nextInt(1, 15)
			val free = (0 until size).filter { it != size - 5 }.shuffled(r).take(n)
			val board = base.toMutableList()
			val order = mutableListOf<Int>()
			for ((k, slot) in free.withIndex()) {
				val named = r.nextInt(5) != 0
				val name = when {
					named -> "${k + 1}"
					r.nextBoolean() -> "Step"
					else -> " ${k + 1} "
				}
				board[slot] = C(if (r.nextInt(12) == 0) "stone" else dyes.random(r), name, if (named) r.nextInt(1, 3) else k + 1 - r.nextInt(0, 2))
				order += slot
			}
			val memo = C("glowstone", "Remember the pattern!", 1)
			val clock = C("clock", "Timer", 1)
			repeat(r.nextInt(1, 3)) { events += Tick(withStatus(board, memo)) }
			if (r.nextInt(6) == 0) events += Click(r.nextInt(-1, size + 1))
			val hidden = base.toMutableList().also { b -> order.forEach { b[it] = C("gray_stained_glass_pane", "?", 1) } }
			events += Tick(withStatus(hidden, if (r.nextInt(10) == 0) null else clock))
			var at = 0
			repeat(r.nextInt(0, n + 4)) {
				val slot = if (r.nextInt(10) < 7 && at < order.size) order[at++] else r.nextInt(0, maxOf(1, size))
				events += Click(slot)
				if (r.nextInt(3) == 0) events += Tick(withStatus(hidden, clock))
			}
			base = hidden
		}
		return Session(false, r.nextBoolean(), r.nextBoolean(), false, events)
	}

	private fun superpairs(r: Random, i: Int): Session {
		val size = if (i % 19 == 0) r.nextInt(0, 6) else 54
		val faces = List(r.nextInt(3, 14)) { k ->
			when (r.nextInt(8)) {
				0 -> C("diamond", "Gained +${k} Powerup", 1)
				1 -> C("experience_bottle", "Instant Find POWERUP", 1)
				else -> C(listOf("enchanted_book", "diamond", "emerald", "paper").random(r), "Card ${k % 5}", 1)
			}
		}
		val hiddenFace = C("purple_stained_glass_pane", "Click a second button!", 1)
		val layout = List(size) { faces.random(r) }
		val hidden = (0 until size).filter { r.nextInt(5) != 0 }.toSet()
		val events = mutableListOf<Ev>()
		repeat(r.nextInt(1, 12)) {
			val shown = (0 until size).filter { r.nextInt(6) == 0 }.toSet()
			val cells = List(size) { s ->
				when {
					s !in hidden -> if (r.nextInt(3) == 0) air else C(fillers.random(r), "", 1)
					s in shown -> layout[s]
					else -> hiddenFace
				}
			}
			events += Tick(cells)
			if (r.nextInt(3) == 0) events += Click(r.nextInt(0, maxOf(1, size)))
		}
		return Session(true, false, false, r.nextBoolean(), events)
	}

	fun sessions(): List<Session> {
		val r = Random(88)
		return List(240) { i -> if (i % 2 == 0) sequencer(r, i) else superpairs(r, i) }
	}
}

object Batch8Minions {
	class Repo(val tiers: Map<String, Int>, val names: Map<String, String>, val recipes: Map<String, List<Pair<String, Double>>>, val prices: Map<String, Double>)
	class Item(val name: String, val lore: List<Pair<String, String>>)

	val repo: Repo by lazy {
		val r = Random(808)
		val bases = listOf("COBBLESTONE_GENERATOR", "WHEAT_GENERATOR", "SNOW_GENERATOR", "INFERNO_GENERATOR", "SLIME_GENERATOR",
			"REVENANT_GENERATOR", "FLOWER_GENERATOR", "SAND_GENERATOR", "RED_SAND_GENERATOR", "MITHRIL_GENERATOR", "VAMPIRE_GENERATOR", "LAPIS_GENERATOR")
		val shown = listOf("Cobblestone", "Wheat", "Snow", "Inferno", "Slime", "Revenant", "Flower", "Sand", "Red Sand", "Mithril", "Vampire", "Lapis")
		val tiers = linkedMapOf<String, Int>()
		val names = mutableMapOf<String, String>()
		val recipes = mutableMapOf<String, List<Pair<String, Double>>>()
		val prices = mutableMapOf<String, Double>()
		val mats = listOf("COBBLESTONE", "ENCHANTED_COBBLESTONE", "WHEAT", "ENCHANTED_BREAD", "SNOW_BALL", "SLIME_BALL", "SAND", "MITHRIL_ORE", "LAPIS_LAZULI")
		for (m in mats) if (r.nextInt(6) != 0) prices[m] = Math.rint(r.nextDouble(0.5, 50_000.0) * 100) / 100
		for ((k, b) in bases.withIndex()) {
			val top = r.nextInt(1, 13)
			tiers[b] = top
			for (t in 1..top) {
				val numeral = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")[t - 1]
				val label = when (r.nextInt(14)) {
					0 -> "§a${shown[k]} Minion §r$numeral"
					1 -> "${shown[k]} Minion $numeral\n"
					2 -> "${shown[k]} Minion"
					3 -> "${shown[k]} Minion  $numeral"
					4 -> "${shown[k]} Minion $numeral\u0085"
					else -> "§9${shown[k]} Minion $numeral"
				}
				if (r.nextInt(15) != 0) names["${b}_$t"] = label
				if (r.nextInt(12) != 0) recipes["${b}_$t"] = List(9) {
					when {
						it == 4 && t > 1 -> "${b}_${t - 1}" to 1.0
						r.nextInt(4) == 0 -> "CORE_SENTINEL_EMPTY" to 0.0
						else -> mats.random(r) to r.nextInt(1, 160).toDouble()
					}
				}
			}
			if (r.nextInt(3) == 0) prices["${b}_1"] = 1234.5
		}
		Repo(tiers, names, recipes, prices)
	}

	val names: List<String> by lazy {
		val r = Random(809)
		val shown = listOf("Cobblestone", "Wheat", "Snow", "Inferno", "Slime", "Revenant", "Flower", "Sand", "Red Sand", "Mithril", "Vampire", "Lapis", "Pigman")
		shown.flatMap { listOf("$it Minion", "$it Minion I", "$it minion", " $it Minion") } + List(20) { "x".repeat(r.nextInt(0, 4)) + " Minion" }
	}

	val crafted: List<Set<String>> by lazy {
		val r = Random(810)
		List(300) {
			buildSet { for ((b, top) in repo.tiers) { val upto = r.nextInt(0, top + 2); for (t in 1..minOf(upto, top)) if (r.nextInt(9) != 0) add("${b}_$t") } }
		}
	}

	val needLines = listOf("Craft 5 more unique minions to unlock your 10th minion slot!", "Craft 0 more unique minions to unlock",
		"Craft 12 more unique minions to unlock", "Craft  5 more unique minions to unlock", "Craft 5x more unique minions to unlock",
		"Craft 5 more unique minions to unlock\u0085x", "Craft 5 more unique minions to unlock\u2028", "craft 5 more unique minions to unlock",
		"Craft 99999999999 more unique minions to unlock", "Craft ５ more unique minions to unlock", "Craft  more unique minions to unlock",
		"Craft 7 more unique minions", "Craft 3 more unique minions to unlocked!", "", "Craft 3 more unique minions to unlock\tnow")

	val menus: List<List<List<Item>>> by lazy {
		val r = Random(811)
		List(120) {
			List(r.nextInt(1, 4)) {
				List(r.nextInt(0, 12)) {
					if (r.nextInt(3) == 0) Item(listOf("Go Back", "Close", "Information").random(r),
						List(r.nextInt(0, 4)) { val l = needLines.filter { "99999999999" !in it }.random(r); l to l })
					else Item(names.random(r).trim(), List(r.nextInt(0, 14)) { t ->
						val text = if (r.nextInt(5) == 0) "Requires Tier" else if (r.nextInt(6) == 0) "Something" else "Tier ${t + 1}"
						text to (if (r.nextBoolean()) "§a" else "§c") + text
					})
				}
			}
		}
	}
}

object Batch8Abiphone {
	class Entry(val requirement: List<String>, val island: String?, val x: Int?, val y: Int?, val z: Int?)
	class Case(val title: String, val sky: Boolean, val fav: Boolean, val only: Boolean, val req: Boolean, val markers: Boolean,
		val name: String, val lore: List<String>, val stars: Set<String>?, val entry: Entry?, val marked: String?, val shift: Boolean)

	val cases: List<Case> by lazy {
		val r = Random(812)
		val titles = listOf("Abiphone X Plus", "Abiphone Shop", "AⒷiphone", "Abiphone", "Contacts Directory", "contacts directory", "Abiphone Contacts", "Chest", "")
		val names = listOf("Elle", "Kat", "Marco", "Trevor", "Jacob", "Fear Mongerer", "Dusk", "Gold Forger", "Spaceman")
		val lores = listOf(listOf("Left-click to call!"), listOf("Click to call!"), listOf("Right-click to remove", "Click to call!"),
			listOf("click to call!"), listOf(" Click to call!"), listOf(), listOf("Requirement: none"))
		fun entry(): Entry? = when (r.nextInt(6)) {
			0 -> null
			1 -> Entry(listOf(), null, null, null, null)
			2 -> Entry(listOf("§cRequires Garden Level 5", "§7and stuff"), "garden", 10, 70, -3)
			3 -> Entry(listOf("§aDone"), null, 4, 5, 6)
			4 -> Entry(listOf(), "hub", 1, null, 3)
			else -> Entry(listOf("§cMining 12"), "mining_3", -50, 120, 33)
		}
		List(4000) {
			Case(titles.random(r), r.nextInt(8) != 0, r.nextInt(5) != 0, r.nextBoolean(), r.nextInt(4) != 0, r.nextInt(4) != 0,
				names.random(r), lores.random(r), if (r.nextInt(10) == 0) null else names.filter { r.nextInt(3) == 0 }.toSet(),
				entry(), if (r.nextBoolean()) names.random(r) else null, r.nextBoolean())
		}
	}
}

object Batch8Bin {
	val labels: List<String> by lazy {
		val r = Random(813)
		val fixed = listOf("Item price: 1,234 coins", "Item price: 1,234,567,890 coins", "Item price: 0 coins", "Item price: , coins",
			"Item price: 12 coin", "Item price:12 coins", "Starting bid: 5 coins", "Item price: 5 coins Item price: 7 coins",
			"Item price: x Item price: 7 coins", "item price: 7 coins", "Item price: 99999999999999999999 coins", "Item price: 1,2,3 coins!",
			"  Item price: 42 coins  ", "Item price: ５ coins", "Item price: 1.5 coins", "Item price:  3 coins", "")
		fixed + List(300) {
			val head = listOf("", "Item price: ", "Price: ", "Item price:", " Price: ", "Buy price: ").random(r)
			val n = List(r.nextInt(0, 14)) { "0123456789,,".random(r) }.joinToString("")
			val tail = listOf(" coins", " coin", "coins", " coins!", " Coins", "").random(r)
			listOf("", "  ", "x ").random(r) + head + n + tail
		} + List(200) {

			val v = r.nextLong(0, 5_000_000_000)
			val num = if (r.nextBoolean()) "%,d".format(java.util.Locale.ROOT, v) else v.toString()
			listOf("", " ", "§6").random(r) + listOf("Item price: ", "Price: ").random(r) + num + listOf(" coins", " coins ", " coins!").random(r)
		}
	}

	val grid: List<List<Any>> by lazy {
		val r = Random(814)
		List(3000) {
			val lowest = when (r.nextInt(6)) { 0 -> 0.0; 1 -> -5.0; else -> Math.rint(r.nextDouble(1.0, 1e9)) / (1 + r.nextInt(3)) }
			val price = when (r.nextInt(5)) { 0 -> 0L; 1 -> (lowest * r.nextDouble(0.0, 3.0)).toLong(); else -> r.nextLong(-5, 2_000_000_000) }
			listOf(price, lowest, r.nextInt(-10, 120), r.nextInt(-10, 400), r.nextBoolean())
		}
	}

	val scripts: List<String> by lazy {
		val r = Random(815)
		List(400) { List(r.nextInt(1, 25)) { listOf("jW", "j-", "yO", "yC", "n", "c").random(r) }.joinToString(" ") }
	}

	val screens = listOf(320 to 240, 427 to 240, 1920 to 1080, 641 to 361, 0 to 0, 255 to 171)
}

object Batch8Auction {
	val searches: List<String> by lazy {
		val r = Random(816)
		val words = listOf("Hyperion", "Wither", "Blade", "Necron", "a", "", "Enchanted", "Diamond", "Block", "Superlongwordthatexceeds", "x", "Aspect", "of", "the", "End", "15charsexactly!")
		listOf("", "short", "exactly15chars!", "sixteen chars ok", "Hyperion Wither Blade Necron", "   ", "a b c d e f g h i j k l m n o p q") +
			List(600) { List(r.nextInt(1, 9)) { words.random(r) }.joinToString(if (r.nextInt(6) == 0) "  " else " ").let { if (r.nextInt(8) == 0) " $it " else it } }
	}
	val signs = listOf(
		listOf("", "", "^^^^^^^^^^^^^^^", "Enter query"), listOf("x", "", "^^^^^^^^^^^^^^^", "Enter query"), listOf("", "", "^^^^^^^^^^^^^^", "Enter query"),
		listOf("", "", "^^^^^^^^^^^^^^^", "Enter Query"), listOf("", "", "^^^^^^^^^^^^^^^"), listOf(), listOf("", "", "^^^^^^^^^^^^^^^", "Enter query", "extra"),
		listOf("", "", "^^^^^^^^^^^^^^^", "Enter amount"))
	val titles = listOf("Auctions Browser", "Auctions: \"hype\"", "Auctions:", "Auction House", "Bazaar ➜ Farming", "Bazaar", "Bazaar Orders", "bazaar",
		"Co-op Bazaar", "", "Auctions Browser ", "Your Bids", "Manage Auctions")
	val pushes: List<List<Pair<String, Int>>> by lazy {
		val r = Random(817)
		List(200) { List(r.nextInt(1, 30)) { listOf("hype", "Hype", " hype ", "", "  ", "term", "juju", "aotd", "x", "ender pearl").random(r) to r.nextInt(0, 7) } }
	}
}

object Batch8Search {
	class Item(val id: String, val display: String, val bazaar: Pair<Double, Long>?, val lowestBin: Double?)

	val items: List<Item> by lazy {
		val r = Random(818)
		val words = listOf("Enchanted", "Diamond", "Block", "Hyperion", "Wither", "Sword", "Pet", "Tiger", "Book", "Aspect", "of", "the", "End", "ULTIMATE", "wise")
		List(700) { i ->
			val name = List(r.nextInt(1, 5)) { words.random(r) }.joinToString(" ")
			val display = listOf("§6", "§a§l", "", "§r ").random(r) + name + listOf("", " ", "§r").random(r)
			val bz = if (r.nextInt(4) == 0) Math.rint(r.nextDouble(0.1, 1e7) * 10) / 10 to r.nextLong(0, 10_000_000) else null
			Item("ITEM_$i", display, bz, if (r.nextInt(3) == 0) null else Math.rint(r.nextDouble(1.0, 1e9)))
		}
	}
	val queries = listOf("e", "ench", "enchanted diamond", "  DIAMOND   block ", "x", "the end", "pet tiger", "of", "sword wither hyperion", "ULT", "wise book", "a", "d b")
	val scrolls = listOf(0, 1, 5, -1, 90)
}

object Batch8Custom {
	val names: List<String> by lazy {
		val r = Random(819)
		val alphabet = "&&&§aAbBkKlLoOrRgGzZ09 xX#\n-"
		listOf("", "&", "&&", "&a", "&&a", "a&", "&aHello &lWorld", "&g&z&R&r", "&\na") + List(800) { List(r.nextInt(0, 12)) { alphabet.random(r) }.joinToString("") }
	}
	val colours: List<String> by lazy {
		val r = Random(820)
		val alphabet = "0123456789abcdefABCDEFgx# +-"
		listOf("", "#", "#FF0000", "ff0000", " #00ff00 ", "##ff", "#-1", "+ff", "#FFFFFFFF", "#7FFFFFFF", "#80000000", "0x10", "  ") +
			List(500) { List(r.nextInt(0, 10)) { alphabet.random(r) }.joinToString("") }
	}
	val ints = listOf(0, 1, 0xFF0000, 0xFFFFFF, 0x1000000, -1, Int.MIN_VALUE, Int.MAX_VALUE, 0xABCDEF, 0x0000FF, 0x12345678)
}
