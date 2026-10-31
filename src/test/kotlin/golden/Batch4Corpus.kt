// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch4Corpus {
	private val calcAtoms = listOf("1", "2", "0", "10", "3.5", "1,000", "12_000", "0.1", ".5", "5.", "7k", "2m", "1b", "1t", "3s", "2e", "50%",
		"enchanted_diamond", "ENCHANTED_DIAMOND", "booster_cookie", "x1", "(1+2)", "(2", "1)", "-3", "+4", "--2", "2^3", "2^-1", "2^0.5", "2^1000",
		"10/0", "1/3", "k", "%", "", " ", "1 2", "1..2", "abc:1", "ink_sack;3", "1kk", "1km", "9e", "2^3^2")
	private val ops = listOf("+", "-", "*", "/", "x", "^", " + ", " * ", "")

	fun calc(): List<String> {
		val r = Random(51)
		val fixed = listOf("", " ", "1+2*3", "(1+2)*3", "2^3^2", "-2^2", "1/3*3", "10k/4", "1.5m-500k", "64s", "3e", "1,234,567", "1e3", "5%*200",
			"((((1))))", "1+", "*2", "2**3", "1 / 0", "0^0", "2^-2", "100000000000000000000*1000000000000", "1t*1t*1t", "ENCHANTED_DIAMOND*3", "unknown+1")
		return fixed + List(500) {
			val n = r.nextInt(1, 6)
			(0 until n).joinToString("") { i -> (if (i > 0) ops[r.nextInt(ops.size)] else "") + calcAtoms[r.nextInt(calcAtoms.size)] }
		}
	}

	val plainNumbers = listOf("1", "-1", "1,000", " 12 ", "1.5", "1.", ".5", "-0.25", "1e3", "abc", "", "1,2,3", "--1", "+1", "1 000")

	private val dmgNums = listOf("1", "999", "1000", "1001", "12345", "999999", "1000000", "1234567", "99999999", "100000000", "1234567890",
		"999999999999", "1000000000000", "123456789012345", "9223372036854775807", "9223372036854775808", "1,234", "12,345,678", "0", "")
	private val colourCodes = listOf("§f", "§e", "§6", "§c", "§7", "§d", "§b")
	private val tails = listOf("", "❤", "⚔", "+", "✧", "♞", "☄", "✷", "ﬗ", "✯", "§r", "§r❤", " hit", "♞❤")

	fun damage(): List<String> {
		val r = Random(52)
		val out = mutableListOf<String>()
		for (n in dmgNums) {
			out += "§7$n"; out += "§c$n❤"; out += "§f$n♞"
			val critDigits = n.mapIndexed { i, c -> colourCodes[i % colourCodes.size] + c }.joinToString("")
			out += "§f✧$critDigits§f✧"; out += "§f✧$critDigits§e✧❤"
			out += "§6✯$critDigits§6✯§r"; out += "§6✯$critDigits§6✯"
		}
		repeat(300) {
			val n = dmgNums[r.nextInt(dmgNums.size)]
			val c = colourCodes[r.nextInt(colourCodes.size)]
			out += when (r.nextInt(4)) {
				0 -> c + n + tails[r.nextInt(tails.size)]
				1 -> "§f✧" + n.map { colourCodes[r.nextInt(colourCodes.size)] + it }.joinToString("") + c + "✧" + tails[r.nextInt(tails.size)]
				2 -> c + "✯" + n.map { colourCodes[r.nextInt(colourCodes.size)] + it }.joinToString("") + c + "✯§r"
				else -> n + tails[r.nextInt(tails.size)]
			}
		}
		return out + listOf("", "§", "hello", "§7Zombie §a100/100❤", "§f✧§f1§f,§f2§f3§f4§f✧")
	}

	val shortNumbers = listOf(0L, 1L, 999L, 1000L, 1001L, 1049L, 1050L, 1099L, 9999L, 10000L, 10049L, 10050L, 99949L, 99950L, 99999L, 100000L, 100499L,
		100500L, 999499L, 999500L, 999999L, 1_000_000L, 1_234_567L, 12_345_678L, 123_456_789L, 999_999_999L, 1_000_000_000L, 1_500_000_000_000L,
		999_999_999_999_999L, Long.MAX_VALUE, -5L, -1000L)

	private val lobbyBits = listOf("mini12B", "mini0A", "mini999Z", "mini1000A", "mega7C", "m73AR", "M5Q", "mini12b", "MINI12B", "mini", "12B", "m7", "3AR",
		"minim12B", "xmini12B", "mini12BC", "Server: ", " ", "§8", "Sending to server ", "...", "lobby", "M", "mega", "1A", "mini05D", "m", "0Z")

	fun segments(): List<List<String>> {
		val r = Random(53)
		return List(400) { List(r.nextInt(1, 5)) { (0 until r.nextInt(1, 3)).joinToString("") { lobbyBits[r.nextInt(lobbyBits.size)] } } }
	}
}
