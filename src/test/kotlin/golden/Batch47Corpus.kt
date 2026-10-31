// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch47Tab {
	val r = Random(4747)
	private val names = listOf("Steve", "alex", "Bob_1", "zed", "AAAAAAAAAAAAAAAAA", "x", "Ünï", "a b", "Alex", "bob_1", "", "Name_With_16chrs")
	private val tails = listOf("", " ♲", " ⚒ ♲", "  ", "x", " [MVP+]", "]", " 12")
	private val levels = listOf("0", "1", "302", "007", "99999999999", "", "-3", "12a")
	fun line(): String {
		val pad = listOf("", " ", "  ")[r.nextInt(3)]
		return when (r.nextInt(8)) {
			0 -> listOf("Players (12)", "Info", "Area: Hub", "", " ", "[302]Steve", "302] Steve", "[302] ", "Server: m1A")[r.nextInt(9)]
			else -> pad + "[" + levels[r.nextInt(levels.size)] + "] " + names[r.nextInt(names.size)] + tails[r.nextInt(tails.size)] + pad
		}
	}
	val lines: List<String> = List(2500) { line() }
	val sections: List<List<String>> = List(700) { List(r.nextInt(0, 12)) { line() } }
}
