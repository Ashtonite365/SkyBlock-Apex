// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch41Boss {
	val r = Random(4141)
	val chat = listOf(
		"[BOSS] Goldor: Who dares trespass into my domain?", "The Core entrance is opening!", "[BOSS] Storm: I should have known that I stood no chance.",
		"[BOSS] Storm: Pathetic Maxor, just like expected.", "[BOSS] Storm: ENERGY HEED MY CALL!", "[BOSS] Storm: THUNDER LET ME BE YOUR CATALYST!",
		"[BOSS] The Professor: Oh? You found my Guardians' one weakness?", "", "x", "[BOSS] Storm: ENERGY HEED MY CALL",
	)
	data class TOp(val chat: Int?, val goldorLoop: Boolean, val padLoop: Boolean, val reset: Boolean)
	val timerScripts: List<List<TOp>> = List(80) {
		List(r.nextInt(20, 400)) { TOp(if (r.nextInt(12) == 0) r.nextInt(chat.size) else null, r.nextInt(3) != 0, r.nextBoolean(), r.nextInt(300) == 0) }
	}
	val formatTimes = listOf(-5, 0, 1, 19, 20, 33, 34, 65, 66, 67, 99, 100, 101, 520, 12345)
	val formatMax = listOf(100, 60, 0, 1, 520, -10)

	val bearXz = listOf(-3 to 33, -9 to 31, 7 to 34, -2 to 33, 34 to 1, 34 to 5, 0 to 0, 33 to 12, -24 to 0, -24 to 2)
	data class BOp(val x: Int, val y: Int, val z: Int, val old: String, val new: String, val master: Boolean, val tick: Int, val reset: Boolean)
	val bearScripts: List<List<BOp>> = List(80) {
		val master = r.nextBoolean()
		List(r.nextInt(10, 120)) {
			val (x, z) = bearXz[r.nextInt(bearXz.size)]
			val flip = r.nextInt(3)
			val (o, n) = when (flip) { 0 -> "coal_block" to "sea_lantern"; 1 -> "sea_lantern" to "coal_block"; else -> "air" to "sea_lantern" }
			BOp(x, if (r.nextInt(10) == 0) 76 else 77, z, o, n, if (r.nextInt(10) == 0) !master else master, r.nextInt(0, 40), r.nextInt(150) == 0)
		}
	}

	data class TerOp(val pot: Triple<Int, Int, Int>?, val master: Boolean, val ticks: Int)
	val terraScripts: List<List<TerOp>> = List(40) {
		List(r.nextInt(5, 40)) { TerOp(if (r.nextInt(3) != 0) Triple(r.nextInt(0, 4), 70, r.nextInt(0, 4)) else null, r.nextBoolean(), r.nextInt(0, 120)) }
	}
	val colourFloats = listOf(-1f, 0f, 2f, 2.0001f, 5f, 5.0001f, 12f, Float.NaN)

	data class DOp(val kind: Int, val x: Double, val y: Double, val z: Double, val count: Int, val flame: Boolean, val dx: Float, val dy: Float, val dz: Float, val n: Int)
	val spawnSpots = listOf(27.0 to 59.0, 85.0 to 56.0, 27.0 to 94.0, 84.0 to 94.0, 56.0 to 125.0, 24.0 to 56.0, 30.0 to 62.0, 23.0 to 91.0, 29.0 to 97.0,
		82.0 to 91.0, 83.0 to 57.0, 27.5 to 59.0, 0.0 to 0.0, -0.0 to 59.0, Double.NaN to 59.0, 56.0 to 122.0, 59.0 to 128.0, 88.0 to 97.0)
	val statues = listOf(Triple(32, 22, 59), Triple(80, 23, 56), Triple(32, 23, 94), Triple(79, 23, 94), Triple(56, 22, 120), Triple(0, 0, 0), Triple(32, 22, 60))
	val dragonScripts: List<List<DOp>> = List(80) {
		List(r.nextInt(10, 300)) {
			val k = r.nextInt(20)
			val (x, z) = spawnSpots[r.nextInt(spawnSpots.size)]
			val good = r.nextInt(6) != 0
			DOp(when { k < 3 -> 0; k < 16 -> 1; k < 18 -> 2; k < 19 -> 3; else -> 4 }, x, if (good || r.nextBoolean()) 19.0 else 19.5, z,
				if (good) 20 else listOf(19, 20, 21)[r.nextInt(3)], good || r.nextBoolean(), if (good) 2f else listOf(2f, 3f, 1.9f)[r.nextInt(3)],
				if (good) 3f else listOf(3f, 2f)[r.nextInt(2)], if (good) 2f else listOf(2f, 2.5f)[r.nextInt(2)], r.nextInt(0, 7))
		}
	}
	val timerTicks = listOf(-100, -1, 0, 1, 19, 20, 21, 59, 60, 61, 100, 1234, Int.MAX_VALUE, 42949673)
	val healths = listOf(0f, -1f, 0.9f, 999f, 999.95f, 1000f, 12345f, 999_999f, 1e6f, 2.5e8f, 249_999_990f, 250_000_000f, 5e8f, 7.5e8f, 7.4999994e8f, 1e9f, 1.23e9f,
		Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 3e9f)
	val boxPoints = listOf(Triple(14.5, 13.0, 45.5), Triple(39.5, 28.0, 70.5), Triple(30.0, 20.0, 60.0), Triple(14.4, 20.0, 60.0), Triple(80.0, 10.0, 60.0),
		Triple(30.0, 20.0, 100.0), Triple(56.0, 20.0, 120.0), Triple(Double.NaN, 20.0, 60.0), Triple(90.0, 20.0, 100.0), Triple(72.0, 8.0, 77.0))
	val relicIds: List<String?> = listOf(null, "", "GREEN_KING_RELIC", "PURPLE_KING_RELIC", "BLUE_KING_RELIC", "ORANGE_KING_RELIC", "RED_KING_RELIC", "green_king_relic", "KING_RELIC")
}
