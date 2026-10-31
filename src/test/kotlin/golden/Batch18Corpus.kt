// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch18Sh {
	private fun <T> pick(r: Random, xs: List<T>) = xs[r.nextInt(xs.size)]

	data class Add(val p: DoubleArray, val player: DoubleArray, val now: Long, val pitch: Double)

	val trails: List<List<Add>> by lazy {
		val r = Random(1801)
		List(120) { t ->
			val player = doubleArrayOf(r.nextDouble(-50.0, 50.0), 70.0, r.nextDouble(-50.0, 50.0))
			val yaw = r.nextDouble(0.0, Math.PI * 2); val climb = r.nextDouble(-0.6, 0.6)
			var x = player[0] + r.nextDouble(-4.0, 4.0); var y = player[1] + 1.5; var z = player[2] + r.nextDouble(-4.0, 4.0)
			var now = 1_000_000L + t * 10_000L
			val step = r.nextDouble(0.2, 0.6)
			List(r.nextInt(1, 30)) { i ->
				val kind = r.nextInt(20)
				when (kind) {
					0 -> Add(doubleArrayOf(x + 10, y, z), player, now + 50, 10.0)
					1 -> Add(doubleArrayOf(x, y, z), player, now + 50, 10.0)
					2 -> { now += 2_500; Add(doubleArrayOf(x, y, z), player, now, 5.0) }
					3 -> Add(doubleArrayOf(Double.NaN, y, z), player, now + 50, 0.0)
					else -> {
						x += step * Math.cos(yaw) + r.nextDouble(-0.02, 0.02); z += step * Math.sin(yaw); y += step * climb
						now += r.nextLong(20, 120)
						Add(doubleArrayOf(x, y, z), player, now, r.nextDouble(-90.0, 90.0))
					}
				}
			}
		}
	}
	val particles = listOf(true, false).flatMap { a -> listOf(0, 1, 2).flatMap { c -> listOf(0f, -0f, 1f, Float.NaN).flatMap { s -> listOf(true, false).map { o -> listOf(a, c, s, o) } } } }
	val scoreboards: List<List<String>> by lazy {
		val r = Random(1802)
		val pool = listOf(" ⏣ Cavern Biome", "⏣ Forest Biome", "Haunted Biome", "⏣ icy biome", "⏣ Icy  Biome", "⏣ The Galatea", "Biome", "⏣ Cavern Biome 2", "⏣ Cavern-Biome",
			"⏣ Moonglade Marsh", "", "Purse: 1,000", "⏣ Fo§rest Biome", "Forest Biome!", "⏣ Ca1vern Biome", "Forest")
		List(200) { List(r.nextInt(0, 5)) { pick(r, pool) } }
	}
	val catches = listOf("You caught a Cavernfish Shard!", "You caught an Areita Shard!", "You captured 2 Foxtrot Shards!", "You captured x2 Foxtrot Shards!", "You captured 3x Foxtrot Shards!",
		"LOOT SHARE You received a Bluebird Shard!", "You received Mantis Shrimp Shard", "You received a mantis shrimp Shards!", "You caught a Cavernfish Shard", "You caught a Zombie Shard!",
		"You caught a  Cavernfish  Shard!", "You caught Hideonwall Shard! (x2)", "You caught a Cavern Fish Shard!", "You caught a Shard!", "You caught a Wumpa's Shard!", "caught a Gazer Shard!",
		"You caught a Gazer Shard!!", "You caught 12 Tepid Shards!!!", "You received an Duplico Shard!", "")
	val caughtSets: List<Set<String>> by lazy {
		val r = Random(1803)
		val all = listOf("Cavernfish", "Flitter", "Shyworm", "Driftling", "Chuckwalla", "Rockmite", "Scrappy", "Snoozle", "Gemzie", "Foxtrot", "Honeybug", "Macaw", "Gazer", "Wumpa", "Tepid", "cavernfish", "Zombie")
		listOf(emptySet(), all.take(9).toSet()) + List(30) { all.filter { r.nextInt(3) == 0 }.toSet() }
	}
	val sprays = listOf("SPRAYONATOR! You sprayed Plot - 12 with Compost!", "SPRAYONATOR! You sprayed Plot - 1 with 2 Honey Jar!", "SPRAYONATOR! You sprayed Plot - A1 with Dung!!",
		"SPRAYONATOR! You sprayed Plot - 12 with !", "SPRAYONATOR! You sprayed Plot -  with Compost!", "SPRAYONATOR! You sprayed Plot - 12 with 3 !", "SPRAYONATOR! You sprayed Plot - 12 with Compost",
		"sprayonator! You sprayed Plot - 12 with Compost!", " SPRAYONATOR! You sprayed Plot - 12 with Compost!", "SPRAYONATOR! You sprayed Plot - 1 with 2 with 3 Tasty Cheese!", "")
	val held = listOf(null, "", "SPRAYONATOR", "JUICY_SPRAYONATOR", "SALTY_SPRAYONATOR", "sprayonator", "SPRAYONATOR_X")
	val remaining = listOf(Long.MIN_VALUE, -1L, 0L, 999L, 1000L, 59_999L, 60_000L, 61_000L, 3_599_999L, 3_600_000L, 36_000_000L, Long.MAX_VALUE)
	val levelLores: List<List<String>> by lazy {
		val r = Random(1804)
		val pool = listOf("Level 1", "Level 25", " Level 50 ", "Level 7/50", "Level 99999999999", "Level x", "level 3", "Level", "Level 0", "Level -1", "Max Level", "", "Level 12 Maxed")
		List(150) { List(r.nextInt(0, 4)) { pick(r, pool) } }
	}
	val names = listOf("Sweep", " Sweep ", "sweep", "Hunter's Luck", "250 Gifts", "Half Full", "Unknown", "")
	val levels = listOf(-1, 0, 1, 2, 5, 9, 10, 24, 25, 39, 40, 41, 49, 50, 51, 90, 99, 100, 101)
}
