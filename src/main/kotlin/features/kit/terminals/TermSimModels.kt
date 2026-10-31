// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.terminals

import kotlin.random.Random

class RubixModel(size: Int = 9, rnd: Random = Random.Default) {
	val cells: IntArray = randomCells(size, rnd)

	fun click(i: Int, right: Boolean) {
		val step = if (right) 4 else 1
		cells[i] = (cells[i] + step) % 5
	}

	val done get() = cells.all { it == cells[0] }

	private companion object {

		fun randomCells(size: Int, rnd: Random): IntArray {
			val board = IntArray(size)
			for (i in 0 until size) board[i] = rnd.nextInt(5)
			if (board.all { it == board[0] }) board[0] = (board[0] + 1) % 5
			return board
		}
	}
}

class PickModel(val labels: List<String>, val matches: (String) -> Boolean) {
	val picked = BooleanArray(labels.size)
	var failed = false
		private set

	fun click(i: Int) {
		if (matches(labels[i])) picked[i] = true else failed = true
	}

	val done: Boolean
		get() {
			if (failed) return false
			for (i in labels.indices) if (matches(labels[i]) && !picked[i]) return false
			return true
		}

	companion object {
		val WORDS = listOf("Apple", "Arrow", "Anvil", "Bone", "Book", "Bread", "Cake", "Carrot", "Coal", "Diamond", "Dirt", "Dandelion",
			"Gold Ingot", "Glass", "Gravel", "Melon", "Map", "Mushroom", "Nether Star", "Name Tag", "Netherrack", "Redstone",
			"Rail", "Rose Bush", "Sand", "Stick", "Sugar", "Torch", "TNT", "Tripwire Hook", "Wheat", "Wool", "Water Bucket")
		val LETTERS = listOf("A", "B", "C", "G", "D", "M", "N", "R", "S", "T", "W")
		val COLOURS = listOf("White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray", "Light Gray", "Cyan",
			"Purple", "Blue", "Brown", "Green", "Red", "Black")
		private val SELECT_KINDS = listOf("Wool", "Glass", "Concrete", "Dye")

		fun startsWith(letter: String, n: Int = 21, rnd: Random = Random.Default): PickModel {
			val hits = WORDS.filter { it.startsWith(letter, ignoreCase = true) }
			val misses = WORDS - hits.toSet()
			val forced = rnd.nextInt(n)
			val labels = List(n) { i ->
				val match = i == forced || rnd.nextDouble() > 0.7
				if (match) hits.random(rnd) else misses.random(rnd)
			}
			return PickModel(labels) { it.startsWith(letter, ignoreCase = true) }
		}

		fun selectAll(colour: String, n: Int = 28, rnd: Random = Random.Default): PickModel {
			val forced = rnd.nextInt(n)
			val labels = List(n) { i ->
				val useColour = i == forced || rnd.nextDouble() > 0.75
				val shown = if (useColour) colour else (COLOURS - colour).random(rnd)
				"$shown ${SELECT_KINDS.random(rnd)}"
			}
			return PickModel(labels) { it.startsWith("$colour ") }
		}
	}
}

class DelayedClicks<T>(private val now: () -> Long = System::currentTimeMillis) {
	private val pending = ArrayDeque<Pair<Long, T>>()

	fun add(click: T, pingMs: Int) {
		pending.addLast(Pair(now() + pingMs, click))
	}

	fun due(): List<T> {
		val out = ArrayList<T>()
		while (pending.isNotEmpty() && pending.first().first <= now()) out.add(pending.removeFirst().second)
		return out
	}

	fun clear() = pending.clear()
}
