// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch21Kit {
	val tags: List<String> by lazy {
		val r = Random(2101)
		val fixed = listOf("", "✯", "✯ Zombie 1.5M❤", "✯ Zombie Commander 1,234❤", "✯ Zombie 12❤", "Zombie 12❤", "✯ Lurker 999,999❤", "✯ Lurker 1,23❤", "✯ Lurker 1234❤",
			"✯ Lost Adventurer 1.5M❤", "lost adventurer", "LOST ADVENTURER 5❤", "✯ Angry Archaeologist 2.1k❤", "✯ Frozen Adventurer 100❤", "✯ Super Archer 50.25❤", "✯Zombie 12❤",
			"✯ Withermancer 12kk❤", "✯ Fels 12❤ ", "[Lv1] ✯ Skeletor 12❤", "✯ Sniper 1,000,000❤", "✯ Spider 0❤", "✯ Cow 12❤", "§c✯ §6Zombie §a12❤", "✯ Souleater 12.❤")
		val mobs = listOf("Zombie", "Lurker", "Cow", "Lost Adventurer", "Super Archer", "Withermancer", "Fels", "Dreadlord")
		val hp = listOf("12", "1,234", "1.5", "999", "1234", "1,2", "", "12k", "1.5M", "5,000,000")
		fixed + List(200) { "${listOf("✯ ", "", "✯", "[Lv5] ✯ ").random(r)}${mobs.random(r)} ${hp.random(r)}${listOf("❤", "", "❤ ", "§c❤").random(r)}" }
	}
	val boxes = listOf(doubleArrayOf(0.0, 0.0, 0.0, 1.0, 2.0, 3.0), doubleArrayOf(-1.5, 60.0, 2.25, 4.0, 61.8, 9.0), doubleArrayOf(5.0, 5.0, 5.0, 1.0, 1.0, 1.0), doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
	val seeds = (0 until 60).map { 2100L + it }
}
