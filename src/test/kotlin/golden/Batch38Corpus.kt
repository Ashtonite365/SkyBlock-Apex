// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch38RiftRepo {
	val vecStrings = listOf("", "1:2:3", " 1 : 2 : 3 ", "1:2", "1:2:3:4", "1:x:2:3", "a:b:c", "1.5:-2e3:NaN", "1:2:Infinity", "-0:0:0",
		"1::2::3", "1;2;3", ":1:2:3:", "0x1:2:3", "1_0:2:3", "1f:2d:3", "١:2:3", "1e400:2:3")

	val jsons = listOf(
		"""{"locations":["1:2:3","4:5:6","bad"],"shortCuts":[{"from":0,"to":2},{"from":1},{"to":3},{"from":"2","to":"x"},{"from":1.5,"to":2},{"from":99999999999,"to":1}]}""",
		"""{"locations":[],"shortCuts":[]}""", """{}""", """{"locations":null}""", """{"locations":"1:2:3"}""", """{"locations":[["1:2:3"]]}""",
		"""{"locations":[null,1,true,"1:1:1"]}""", """{"shortCuts":[1]}""", """{"shortCuts":[{"from":[1],"to":2}]}""", """{"shortCuts":[{"from":1,"to":[2]}]}""",
		"""{"shortCuts":[{"from":null,"to":[2]}]}""", """[]""", """"str"""", """null""", """5""",
		"""{"instructions":["move","sneak jump","Punch  stand",""," x"]}""", """{"instructions":[]}""", """{"instructions":[1,null]}""", """{"instructions":[{}]}""",
		"""{"field_centers":[{"position":"1:2:3","count":5},{"position":"1:2:3","count":7},{"position":"4:5:6"},{"count":3},{"position":"bad","count":1},{"position":"7:8:9","count":"9"},{"position":"0:0:0","count":1.0}]}""",
		"""{"field_centers":[{"position":[1],"count":1}]}""", """{"field_centers":[1]}""", """{"field_centers":[{"position":"1:1:1","count":[1]}]}""",
		"""{"field_centers":[{"position":null,"count":1}]}""",
		"""{"areas":{"Wyld Woods":[{"name":"A","position":"1:2:3"},{"name":"B"},{"position":"1:1:1"},{"name":"A","position":"9:9:9"},5,{"name":"C","position":"bad"}],"Empty":[],"Odd":{},"N":null}}""",
		"""{"areas":[]}""", """{"areas":{"x":[{"position":[1],"name":"z"}]}}""", """{"areas":{"x":[{"position":"1:1:1","name":[1]}]}}""", """{"areas":{"x":[{"name":[1]}]}}""",
		"""{"houses":{"Dreadfarm":[{"position":"1:2:3","buttons":["1:2:4","1:2:5"]},{"position":"bad","buttons":[1]},{"buttons":["1:1:1"]},7],"Village":[{"position":"5:5:5"}],"Bad":3}}""",
		"""{"houses":{"H":[{"position":"1:1:1","buttons":"x"}]}}""", """{"houses":{"H":[{"position":"1:1:1","buttons":[[1]]}]}}""", """{"houses":[]}""",
		"""{"a":{"Name":"Dreadfarm House","Position":"1:2:3","Tags":["rift_buttons_quest"]},"b":{"Position":"bad"},"c":{"Name":null,"Position":"0:0:0"},"d":{"Position":"1:1:1","Tags":[]}}""",
		"""{"a":5}""", """{"a":{"Position":[1]}}""", """{"a":{"Position":"1:1:1","Tags":[1,true,null]}}""", """{"a":{"Position":"1:1:1","Tags":"x"}}""",
		"""{"a":{"Position":"1:1:1","Name":{}}}""",
	)

	val parkourLocs: List<List<Triple<Double, Double, Double>>> = listOf(
		listOf(),
		listOf(Triple(0.0, 0.0, 0.0)),
		(0 until 8).map { Triple(it * 2.0, 0.0, 0.0) },
		(0 until 12).map { Triple(it * 1.0, (it % 3).toDouble(), 0.0) },
	)
	val parkourCuts = listOf(listOf(), listOf(0 to 3, 1 to 5, 2 to 99, 4 to -1, 5 to 7, 7 to 2, -1 to 0))

	data class Step(val x: Double, val y: Double, val z: Double, val ground: Boolean, val look: Int?, val reset: Boolean)

	val parkourScripts: List<List<Step>> = buildList {
		add((0 until 12).map { Step(it * 2.0 + 0.5, 1.0, 0.5, true, null, false) })
		add((0 until 12).map { Step(it * 1.0 + 0.5, (it % 3) + 1.0, 0.5, true, null, false) })
		val r = Random(3838)
		repeat(40) {
			add(buildList {
				repeat(r.nextInt(10, 60)) {
					val k = r.nextInt(10)
					val x = when {
						k < 6 -> r.nextInt(-1, 16) + 0.5 + r.nextDouble(-0.6, 0.6)
						k < 8 -> r.nextDouble(-30.0, 40.0)
						else -> 0.5
					}
					val y = r.nextInt(0, 4) + 1.0 + r.nextDouble(-0.4, 0.4)
					add(Step(x, y, 0.5 + r.nextDouble(-0.5, 0.5), r.nextInt(5) != 0, if (r.nextInt(15) == 0) r.nextInt(-1, 6) else null, r.nextInt(40) == 0))
				}
			})
		}
	}

	val danceLines = listOf(listOf(), listOf("move"), listOf("move", "sneak", "jump", "punch", "stand", "jump sneak", "move  punch", "", "Ünder ßtep", " lead"))
	val danceIdx = listOf(-3, -1, 0, 1, 2, 5, 9, 12, Int.MAX_VALUE)
	val danceTicks = listOf(0, -5, 1, 19, 20, 33, 400, 42949673, Int.MAX_VALUE, Int.MIN_VALUE)
	val danceShow = listOf(0, 1, 2, 3, 5, -1, Int.MAX_VALUE)

	val sounds = listOf("block.note_block.bass", "entity.player.burp", "entity.player.levelup", "", "block.note_block.harp")
	val floats = listOf(0.6984127f, 0.52380955f, 1.8412699f, 1f, 0.8f, 0.0f, 0.69841276f, Float.NaN, 0.8000001f)

	val heartLines = listOf("Effigies: ", "Effigies: ⧯⧯", "Effigies: §7⧯§c⧯§7⧯", "Effigies: §7⧯⧯§c⧯", "Effigies: §c⧯§c⧯§c⧯§c⧯§c⧯§c⧯",
		"Effigies: §a⧯", "Effigies:§7⧯", "x Effigies: §7⧯", "Effigies: §7⧯ ", "Effigies: §7§c⧯", "", "Effigies: §c")
	val timerLines = listOf("§eRespawn §c14m59s §7(or click!)", "§eRespawn §c §7(or click!)", "§eRespawn §c1s §7(or click!)x")
	val durations = listOf("", "14m59s", "1s", "1h2m3s", "5ms", "5 m", "2h", "99999999999999999999s", "3074457345618258h", "1m1m", "x", "0s", "12")
	val millis = listOf(0L, 999L, 1000L, 59_999L, 60_000L, 61_000L, 3_600_000L, -1L, -5000L, Long.MAX_VALUE, Long.MIN_VALUE, 7_384_123L)

	val regexStrings = listOf("Enigma: Soul", "✔ Enigma: Soul", "✔ Enigma: ", "Enigma:  x", "To Rift Guide ➜ Wyld Woods", "To Rift Guide ➜ ",
		"SOUL! You unlocked an Enigma Soul!", "You have already found that Enigma Soul!", "SOUL! You unlocked an Enigma Soul!x", "Enigma Souls", "(1/42) Enigma Souls",
		"Enigma Souls ", "§eYou have hit §r§b12/56 §r§eof the wooden buttons!", "§eYou have hit §r§b/56 §r§eof the wooden buttons!", "")

	val r = Random(383838)
	val soulMaps: List<Map<String, Map<String, Triple<Double, Double, Double>>>> = buildList {
		add(emptyMap())
		add(mapOf("A" to mapOf("s1" to Triple(0.0, 0.0, 0.0), "s2" to Triple(3.0, 0.0, 0.0)), "B" to mapOf("s3" to Triple(-3.0, 0.0, 0.0)), "C" to emptyMap()))
		repeat(20) { add((0 until r.nextInt(1, 5)).associate { a -> "area$a" to (0 until r.nextInt(0, 6)).associate { s -> "soul$a.$s" to Triple(r.nextInt(-10, 11).toDouble(), r.nextInt(-3, 4).toDouble(), r.nextInt(-10, 11).toDouble()) } }) }
	}
	val players: List<Triple<Double, Double, Double>> = buildList {
		add(Triple(0.0, 0.0, 0.0)); add(Triple(8.0, 0.0, 0.0)); add(Triple(Double.NaN, 0.0, 0.0)); add(Triple(1.5, 0.0, 0.0))
		repeat(25) { add(Triple(r.nextInt(-12, 13).toDouble(), r.nextInt(-2, 3).toDouble(), r.nextInt(-12, 13) * 1.0)) }
	}

	data class BerOp(val respawn: Boolean, val x: Int, val now: Long)
	val berberisScripts: List<Pair<Int, List<BerOp>>> = buildList {
		add(3 to listOf(BerOp(true, 1, 0), BerOp(true, 2, 100), BerOp(true, 2, 150), BerOp(true, 3, 200), BerOp(false, 1, 0), BerOp(false, 2, 0), BerOp(false, 3, 0), BerOp(false, 1, 0), BerOp(true, 5, 300)))
		add(2 to listOf(BerOp(true, 1, 0), BerOp(true, 2, 5000), BerOp(true, 3, 5100), BerOp(false, 2, 0)))
		add(1 to listOf(BerOp(true, 1, Long.MIN_VALUE), BerOp(true, 1, Long.MAX_VALUE)))
		add(0 to listOf(BerOp(true, 1, 0), BerOp(true, 2, 1), BerOp(false, 1, 0)))
		repeat(80) {
			add(r.nextInt(1, 6) to buildList {
				var t = r.nextLong(-5000, 5000)
				repeat(r.nextInt(5, 50)) { add(BerOp(r.nextInt(3) != 0, r.nextInt(0, 5), t)); t += listOf(0L, 50L, 900L, 3000L, 3001L)[r.nextInt(5)] }
			})
		}
	}
	val rainbow = (-30..30).toList() + listOf(Int.MAX_VALUE, Int.MIN_VALUE, 1000, -1000, 12345)
}
