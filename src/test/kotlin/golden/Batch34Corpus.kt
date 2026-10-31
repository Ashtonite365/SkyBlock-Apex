// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch34Hoppity {
	private val pool = listOf(
		"HOPPITY'S HUNT You found a Chocolate Breakfast Egg on a ledge!", "HOPPITY'S HUNT You found a Chocolate Dinner Egg behind the barn!", "HOPPITY'S HUNT You found an Hitman Egg somewhere!",
		"HOPPITY'S HUNT You found a Stray Egg!", "HOPPITY'S HUNT You found an Odd Egg!", "HOPPITY'S HUNT You found a Egg!", "HOPPITY'S HUNT You found a Weird Egg near here",
		"You bought Rabbit Egg for 5,000,000 Coins!", "You bought Something!", "You bought !",
		"HOPPITY'S HUNT You found Fluffy (LEGENDARY)!", "HOPPITY'S HUNT You found Bob Jr (COMMON)!", "HOPPITY'S HUNT You found Weird (rare)!", "HOPPITY'S HUNT You found  (EPIC)!",
		"NEW RABBIT! +2 Chocolate and +0.003x Chocolate per second!", "NEW RABBIT! ", "NEW RABBIT! x",
		"DUPLICATE RABBIT! +1,234,567 Chocolate", "DUPLICATE RABBIT! +5 Chocolate", "DUPLICATE RABBIT! +, Chocolate", "DUPLICATE RABBIT! +99999999999999999999999 Chocolate", "DUPLICATE RABBIT! +1,2 Chocolate!",
		"random chat", "",
	)
	val scripts: List<List<String>> by lazy {
		val r = Random(3401)
		val eggs = pool.subList(0, 10)
		val rabbits = pool.subList(10, 14)
		val closers = pool.subList(14, 22)
		val structured = List(80) {
			buildList {
				repeat(r.nextInt(1, 6)) {
					add(eggs[r.nextInt(eggs.size)])
					if (r.nextInt(6) > 0) add(rabbits[r.nextInt(rabbits.size)])
					if (r.nextInt(5) > 0) add(closers[r.nextInt(closers.size)])
					if (r.nextInt(4) == 0) add(pool[r.nextInt(pool.size)])
				}
			}
		}
		List(60) { List(r.nextInt(3, 40)) { pool[r.nextInt(pool.size)] } } + listOf(pool) + structured
	}
}
