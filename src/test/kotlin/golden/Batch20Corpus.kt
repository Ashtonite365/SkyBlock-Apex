// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch20Kit {
	val panes: List<Map<Int, Int>> by lazy {
		val r = Random(2001)
		listOf(emptyMap<Int, Int>(), mapOf(12 to 0), mapOf(12 to 4, 13 to 0), mapOf(1 to 7, 2 to -1)) +
			List(300) { val m = LinkedHashMap<Int, Int>(); repeat(r.nextInt(1, 10)) { m[r.nextInt(10, 35)] = if (r.nextInt(15) == 0) r.nextInt(-3, 9) else r.nextInt(0, 5) }; m }
	}
	val melody = listOf(-1, 0, 1, 8, 9, 10, 17, 18, 26, 44, -9)
	val clicks = listOf(Triple(0L, 0L, 0), Triple(0L, 100L, 350), Triple(0L, 349L, 350), Triple(0L, 350L, 350), Triple(1000L, 0L, 350), Triple(0L, 10L, -1), Triple(Long.MIN_VALUE, Long.MAX_VALUE, 5))
	val distances = listOf(0f, 1f, 4f, 3.6f, -4f, Float.NaN, Float.POSITIVE_INFINITY, 1e30f, 0.1f)
	val jsons = listOf("", "{}", "[]", """{"lineups":[]}""", """{"lineups":{}}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":[[1,2,3]],"targets":[[4,5,6],[7,8,9]]}]}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":[],"targets":[]}]}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":[[1,2]],"targets":[]}]}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":[[1,2,3,4]],"targets":[]}]}""",
		"""{"lineups":[{"supply":1,"colour":null,"start":[[1,2,3]],"targets":[]}]}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":[["a",2,3]],"targets":[]}]}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":[[1.5,2,3]],"targets":[]}]}""",
		"""{"lineups":[{"supply":"x","colour":"red","start":{},"targets":[]}]}""",
		"""{"lineups":[{"supply":"x","start":[],"targets":[]}]}""",
		"""{"lineups":[{"colour":"red"}]}""",
		"""{"lineups":[1]}""", "not json")
	val players: List<Triple<Double, Double, Double>> by lazy {
		val r = Random(2002)
		listOf(Triple(0.0, 0.0, 0.0), Triple(Double.NaN, 0.0, 0.0), Triple(-100.5, 80.0, -100.5)) + List(40) { Triple(r.nextDouble(-150.0, 0.0), r.nextDouble(60.0, 110.0), r.nextDouble(-150.0, 0.0)) }
	}
	val synthLineups: List<List<Triple<Int, Int, Int>>> by lazy {
		val r = Random(2003)
		List(8) { i -> if (i == 3) emptyList() else List(r.nextInt(1, 4)) { Triple(r.nextInt(-140, 0), r.nextInt(70, 100), r.nextInt(-140, 0)) } }
	}
	val colours = listOf("red", "light_purple", "yellow", "aqua", "green", "blue", "white", "", "RED", "gold")
}
