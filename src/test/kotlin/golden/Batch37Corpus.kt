// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch37Rift {
	val strings = listOf(
		"", " ", "Hacking", "Hacking (As seen on CSI)", "hacking", "Hacking ", "Hacking (As seen on CSI) ",
		"ORB! Picked up +12 Motes.", "ORB! Picked up +1,234 Motes!!", "ORB! Picked up + Motes", "ORB! Picked up +12 Motes",
		"You've set the color of this terminal to Red!", "You've set the color of this terminal to !", "You've set the color of this terminal to Light Blue!!",
		"PUNCHCARD! You punched [MVP+] Steve and both regained 5 Motes", "PUNCHCARD! You punched Alex_1 and both regained stuff",
		"PUNCHCARD! You punched [VIP] and both regained x", "PUNCHCARD! You punched [a] [b] c and both regained ",
		"AWKWARD! This player has already been punched by you... somehow!", "AWKWARD! This player has already been punched by you... somehow",
		"UH OH! You reached the limit of 20 players you can punch in one session!",
		"SPLIT! You need to wait 1h 2m 3s before you can play again.", "SPLIT! You need to wait  before you can play again.",
		"SPLIT! You need to wait 3d before you can play again.", "SPLIT! You need to wait x before you can play again..",
		"[|||||] x12 [|||]", "Combo [ab] x5 c]", "[x] x 5 ]", "[] x0 ]", "[a] x99999999999 b]", "x5 ]", "[a] x5 b] extra",
		"[[a]] x7 [b]]", "§a[|||] x3 §7[ok]", "[a] x-1 b]",
	)

	val durations = listOf(
		"", "5s", "1h 2m 3s", "3d", "10 m", "1d2h3m4s", "7x", "m", "12", "2h and 30m", "0s", "1 d 1 h", "4ms", "99999999999999999999s",
		"9223372036854775807s", "106751991167300d", "1d 1d 1d", "5S", "1h,2m", "١٢s", "3m 3m",
	)

	val cruxLores = listOf(
		listOf("§7I Shy: 3/10", "§7II Bob: MAXED", "nothing"),
		listOf("Crux: IV Wrath: 0/0"),
		listOf("  1 Zom: 5/5 kills", "- 2-3 Fly: 1/2"),
		listOf(),
		listOf("V Big: 1/2"), listOf("IV Big:MAXED"), listOf("IV  Big :1/2"),
		listOf("III Lad: 99999999999/2"), listOf("III Lad: 1/0", "III Lad: 0/0"),
		listOf("xIIx Name: 4/8", "IV Name_2: 3/9", "I-V a: MAXED done"),
		listOf("§6I Shy: §a3§7/§a10"), listOf("I Shy: 3/10", "I Shy: 3/10", "II Shy: 10/10"),
	)

	val guideLores = listOf(
		listOf(), listOf("To Rift Guide"), listOf("To Rift Guide!"), listOf("to Rift Guide"), listOf("To Rift Guide", "x"),
		listOf(" To Rift Guide"), listOf("✖ Not completed yet!"), listOf("a", "  ✖ Not completed yet!  "), listOf("✖ Not completed yet!", "b"),
		listOf("✔ Completed!"), listOf("✖ Not completed yet"),
	)

	val fractions = listOf(-1.0, -0.0, 0.0, 0.04, 0.05, 0.049999, 0.5, 0.33, 0.999, 1.0, 1.5, Double.NaN,
		Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.1, 0.7)
	val lens = listOf(20, 0, 1, 7, 10, -1, -5)

	val blocks = listOf("red_mushroom", "brown_mushroom", "", "RED_MUSHROOM", "minecraft:red_mushroom", "stone")
	val elapsed: List<Long?> = listOf(null, 0L, 49L, 50L, 150L, 1234L, -50L, -51L, 99999L, Long.MAX_VALUE, Long.MIN_VALUE)
	val held: List<String?> = listOf(null, "", "LARVA_HOOK", "EMPTY_ODONATA_BOTTLE", "larva_hook", "ODONATA_BOTTLE")

	val points: List<Triple<Double, Double, Double>> = buildList {
		val terms = listOf(Triple(-69.0, 65.0, -63.0), Triple(-44.0, 71.0, -62.0), Triple(-39.0, 71.0, -95.0), Triple(-62.0, 71.0, -83.0),
			Triple(-33.0, 70.0, -134.5), Triple(-66.5, 72.0, -119.0), Triple(-89.0, 73.0, -115.0), Triple(-110.0, 73.0, -107.0))
		for (t in terms) for (d in listOf(0.0, 1.0, 3.0, 4.99, 5.0, 5.01, 7.0, -5.0)) {
			add(Triple(t.first + d, t.second, t.third)); add(Triple(t.first, t.second + d, t.third)); add(Triple(t.first, t.second, t.third + d))
		}

		add(Triple(-65.5, 68.0, -73.0)); add(Triple(-41.5, 71.0, -78.5))
		val r = Random(37)
		repeat(300) { add(Triple(-120 + r.nextDouble() * 100, 60 + r.nextDouble() * 20, -140 + r.nextDouble() * 90)) }
		add(Triple(Double.NaN, 65.0, -63.0)); add(Triple(Double.POSITIVE_INFINITY, 0.0, 0.0)); add(Triple(0.0, 0.0, 0.0))
	}
	val maxes = listOf(5.0, 0.0, 30.0, 1000.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)

	val menus: List<Map<Int, String>> = buildList {
		val r = Random(3737)
		val colours = listOf("A", "B", "C", "D")
		add(emptyMap())
		add(mapOf(2 to "A", 11 to "A"))
		add(mapOf(2 to "A", 3 to "B", 4 to "C", 5 to "D", 6 to "A", 11 to "A", 21 to "C", 31 to "C", 41 to "D", 51 to "B"))
		repeat(40) {
			val m = HashMap<Int, String>()
			for (s in 0 until 60) if (r.nextInt(4) != 0) m[s] = colours[r.nextInt(colours.size)]
			add(m)
		}
	}
	val slots = (-2..60).toList()

	sealed interface OrbOp
	data class Particle(val x: Double, val y: Double, val z: Double, val t: Long) : OrbOp
	data class Update(val t: Long) : OrbOp
	data class PickNear(val x: Double, val y: Double, val z: Double) : OrbOp

	val orbScripts: List<List<OrbOp>> = buildList {

		add(buildList { for (i in 0 until 80) add(Particle(1.0, 2.0, 3.0, 1000L + i * 13)); add(Update(2100)); add(Update(2500)); add(Update(8000)) })

		add(buildList { for (i in 0 until 200) add(Particle(0.0, 0.0, 0.0, i * 5L)); for (i in 0 until 100) add(Particle(0.1, 0.0, 0.0, 1000L + i * 30)) })

		add(buildList { for (i in 0 until 100) { add(Particle(0.0, 0.0, 0.0, i * 14L)); add(Particle(10.0, 0.0, 0.0, i * 14L + 1)) }; add(PickNear(9.0, 0.0, 0.0)); add(Particle(10.0, 0.0, 0.0, 1500)); add(Update(1500)) })
		val r = Random(373)
		repeat(60) {
			add(buildList {
				var t = r.nextLong(0, 1000)
				repeat(r.nextInt(20, 200)) {
					when (r.nextInt(20)) {
						0 -> add(Update(t))
						1 -> add(PickNear(r.nextDouble(-6.0, 6.0), 0.0, r.nextDouble(-6.0, 6.0)))
						else -> add(Particle(r.nextDouble(-4.0, 4.0), r.nextDouble(-1.0, 1.0), r.nextDouble(-4.0, 4.0), t))
					}
					t += listOf(0L, 5L, 12L, 14L, 16L, 40L, 350L, 6000L)[r.nextInt(8)].let { if (it >= 350 && r.nextInt(3) != 0) 13L else it }
				}
				add(Update(t + 400))
			})
		}
	}

	sealed interface SnakeOp
	data class Add(val x: Int, val y: Int, val z: Int, val t: Long) : SnakeOp
	data class Remove(val x: Int, val y: Int, val z: Int) : SnakeOp
	data class Expire(val t: Long) : SnakeOp

	val snakeScripts: List<List<SnakeOp>> = buildList {
		add(listOf(Add(0, 0, 0, 0), Add(1, 0, 0, 10), Add(2, 0, 0, 20), Add(2, 0, 0, 25), Add(0, 0, 0, 30), Remove(1, 0, 0), Expire(30_020), Expire(30_021), Expire(30_031)))
		add(listOf(Add(0, 0, 0, 0), Add(1, 1, 1, 1), Add(2, 2, 2, 2), Add(1, 1, 0, 3), Remove(0, 0, 0), Remove(1, 1, 1), Add(5, 5, 5, 4)))
		add(listOf(Add(Int.MAX_VALUE, 0, 0, 0), Add(Int.MIN_VALUE, 0, 0, 1), Add(46341, 0, 0, 2), Add(0, 0, 0, 3)))
		val r = Random(7373)
		repeat(80) {
			add(buildList {
				var t = 0L
				repeat(r.nextInt(5, 60)) {
					val x = r.nextInt(-3, 4); val y = r.nextInt(-1, 2); val z = r.nextInt(-3, 4)
					when (r.nextInt(10)) {
						0, 1 -> add(Remove(x, y, z))
						2 -> add(Expire(t))
						else -> add(Add(x, y, z, t))
					}
					t += listOf(1L, 100L, 5000L, 29_999L, 30_001L)[r.nextInt(5)]
				}
				add(Expire(t))
			})
		}
	}
}
