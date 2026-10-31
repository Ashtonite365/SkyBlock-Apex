// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch42Rooms {
	val r = Random(4242)
	val jsons = listOf(
		"""{"rooms":[{"name":"A","type":"PUZZLE","shape":"1x1","cores":[1,2],"maxSecrets":3,"crypts":0}]}""",
		"""{"rooms":[{"name":"A","shape":"L"}]}""",
		"""{"rooms":[{"name":"A","type":null,"shape":"2x2","cores":[],"maxSecrets":null,"crypts":null}]}""",
		"""{"rooms":[{"name":"A","shape":"1x4","maxSecrets":"7","crypts":"x"}]}""",
		"""{"rooms":[{"name":"A","shape":"1x4","maxSecrets":2.5,"crypts":true}]}""",
		"""{"rooms":[{"name":"A","shape":"1x4","maxSecrets":99999999999}]}""",
		"""{"rooms":[{"shape":"1x1"}]}""", """{"rooms":[{"name":"A"}]}""", """{"rooms":[{"name":null,"shape":"1x1"}]}""",
		"""{"rooms":[{"name":"A","shape":"1x1","cores":null}]}""", """{"rooms":[{"name":"A","shape":"1x1","cores":["q"]}]}""",
		"""{"rooms":[{"name":"A","shape":"1x1","cores":{}}]}""", """{"rooms":[{"name":{},"shape":"1x1"}]}""",
		"""{"rooms":[{"name":"A","shape":"1x1","type":[1]}]}""", """{"rooms":[{"name":"A","shape":"1x1","crypts":[1]}]}""",
		"""{"rooms":[1]}""", """{"rooms":{}}""", """{"rooms":null}""", """{}""", "[]", "1", "", "{", """{"rooms":[]}""",
		"""{"rooms":[{"name":"A","shape":"1x1"},{"name":"B","shape":"weird","cores":[5]}]}""",
	)
	val ids = listOf("minecraft:stone", "minecraft:gold_block", "minecraft:bedrock", "minecraft:oak_planks", "minecraft:chest", "minecraft:trapped_chest",
		"minecraft:air", "minecraft:cobblestone", "minecraft:blue_terracotta", "minecraft:stone_bricks")

	val columns: List<List<Pair<String, Boolean>>> = List(1500) {
		val lead = r.nextInt(0, 140)
		val style = r.nextInt(4)
		List(129) { i ->
			val y = 140 - i
			when {
				i < lead -> if (r.nextInt(6) == 0) "minecraft:gold_block" to false else (if (r.nextBoolean()) "minecraft:air" else "minecraft:cave_air") to true
				style == 0 && y in 60..72 && r.nextInt(3) != 0 -> "minecraft:bedrock" to false
				style == 1 && r.nextInt(4) == 0 -> "minecraft:air" to true
				style == 2 -> if (y < 75 && r.nextInt(3) == 0) "minecraft:bedrock" to false else if (r.nextInt(5) == 0) "minecraft:air" to true else ids[r.nextInt(ids.size)] to false
				else -> ids[r.nextInt(ids.size)].let { it to (it == "minecraft:air") }
			}
		}
	}
	val shapes = listOf("1x1", "1x2", "1x3", "1x4", "2x2", "L", "weird", "")
	fun randomCells(): List<Pair<Int, Int>> {
		val k = r.nextInt(6)
		val n = r.nextInt(0, 5)
		return when (k) {
			0 -> { val x = r.nextInt(0, 5); val z = r.nextInt(0, 5); listOf(x to z, x + 1 to z, x to z + 1, x + 1 to z + 1).shuffled(r).take(if (n == 0) 3 else n) }
			1 -> { val x = r.nextInt(0, 6); val z = r.nextInt(0, 3); List(maxOf(1, n)) { x to z + it }.shuffled(r) }
			2 -> { val x = r.nextInt(0, 3); val z = r.nextInt(0, 6); List(maxOf(1, n)) { x + it to z }.shuffled(r) }
			3 -> List(n) { r.nextInt(-2, 7) to r.nextInt(-2, 7) }
			4 -> listOf(r.nextInt(0, 3) to 1, r.nextInt(0, 3) to 1)
			else -> listOf(1 to 2_000, 3 to 0, 2 to 1000, 3 to -1, Int.MAX_VALUE to 1, 0 to 0).shuffled(r).take(maxOf(1, n))
		}
	}
	val geomCases: List<Pair<String, List<Pair<Int, Int>>>> = List(3000) { shapes[r.nextInt(shapes.size)] to randomCells() }

	data class Det(val cells: List<Pair<Int, Int>>, val choice: List<Int>, val highest: Int, val clayMask: Int, val sameRoom: Boolean)
	val detCases: List<Det> = List(3000) {
		val cells = randomCells().ifEmpty { listOf(0 to 0) }
		Det(cells, List(cells.size) { listOf(-1, -2, -3, r.nextInt(0, 140), r.nextInt(0, 140))[r.nextInt(5)] }, r.nextInt(60, 110), r.nextInt(16), r.nextInt(3) != 0)
	}
	val coords = List(60) { Triple(r.nextInt(-300, 300), r.nextInt(0, 255), r.nextInt(-300, 300)) } + listOf(Triple(Int.MAX_VALUE, 1, Int.MIN_VALUE), Triple(0, 0, 0))
}
