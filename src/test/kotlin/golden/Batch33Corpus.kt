// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch33Rift {
	private val lorePool = listOf("Sell Price: 1,234 Motes", "Motes Sell Price: 12.5 Motes (each)", "  Sell Price: 1.2.3 Motes", "Sell Price: , Motes", "Sell Price: 99 motes", "§7Sell Price: §d50 Motes",
		"Sell Price: 7 Motes", "NPC Sell Price: 5 Motes", "", "Rarity")
	val lores: List<List<String>> by lazy { val r = Random(3301); List(150) { List(r.nextInt(5)) { lorePool[r.nextInt(lorePool.size)] } } }
	private val boardPool = listOf("Motes: 1,234", " Motes: 99999999999999999999", "Motes: 12 (+3)", "Motes: ,", "Motes: 5", "motes: 5", "Time left: 12m 3s", "ф Left: 45s", "Purse: 5", "")
	val boards: List<List<String>> by lazy { val r = Random(3302); List(150) { List(r.nextInt(5)) { boardPool[r.nextInt(boardPool.size)] } } }
	val times = listOf("", "12m 3s", "45s", "1h", "1h2m3s", "3x", "0s", "99999999999s", "2147483647s 1s", "5d 4h", "m s h", "12 m", "-5s", "10ms", "1h 1h")
	val numbers = listOf("1,234", "1.5", "", ",", "1e5", "NaN", "Infinity", "1,2,3.4", "-1", "0x10")
}
