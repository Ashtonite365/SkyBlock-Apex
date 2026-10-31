// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.random.Random

object Batch31Fish {
	val geyser: List<List<Any>> by lazy {
		val r = Random(3101)
		val fs = listOf(0.1f, 0.6f, 0.05f, 0.0f, -0.0f, Float.NaN, 0.1000001f)
		listOf<List<Any>>(listOf(true, 15, 0.05f, 0.1f, 0.6f, 0.1f)) +
			List(300) { listOf(r.nextInt(5) > 0, if (r.nextBoolean()) 15 else r.nextInt(20), fs[r.nextInt(fs.size)], fs[r.nextInt(fs.size)], fs[r.nextInt(fs.size)], fs[r.nextInt(fs.size)]) }
	}
	val boxes = listOf(0.0, -0.0, 1.5, -373.25, Double.NaN, 1e9)
	val lores = listOf(listOf(), listOf("TROPHY FISH"), listOf("  §6§lLEGENDARY TROPHY FISH  "), listOf("TROPHY FISH!", "x"), listOf("trophy fish"), listOf("a", "BRONZE TROPHY FISH"), listOf(""))
	val flay: List<List<Any>> by lazy {
		val r = Random(3102)
		val ds = listOf(1.0, 0.0, 0.23137255012989044, 0.05098039284348488, 0.0313725508749485, 0.003921568859368563, 0.0039215, 1.0000009, 0.999998, Double.NaN, -0.0)
		List(400) { listOf(r.nextInt(6) > 0, ds[r.nextInt(ds.size)], ds[r.nextInt(ds.size)], ds[r.nextInt(ds.size)], if (r.nextInt(4) > 0) 0 else r.nextInt(-1, 3), if (r.nextInt(4) > 0) 1.0f else 0.5f) }
	}
	val names = listOf("Ice Bait", "Bait", " Bait", "Glowy Chum Bait!", "Sponge", "", "§aIce Bait")
	val counts = listOf(Int.MIN_VALUE, -1, 0, 1, 2, 64)
	val cocoon = listOf("CAUGHT! You cocooned a Yeti!", "CAUGHT! You cocooned an Lord Jawbus!", "x CAUGHT! You cocooned a Thunder! y", "CAUGHT! You cocooned a !", "CAUGHT! You cocooned a Grim-Reaper!",
		"§aCAUGHT! §7You cocooned a §cReindrake!", "CAUGHT! You cocooned a  The Loch Emperor!", "")
	val lists = listOf("", ",", " a , B,,c ,A", "Lord Jawbus, Thunder, Plhlegblast, The Loch Emperor, Reindrake, Great White Shark, Grim Reaper, Phantom Fisher, Yeti", "YETI", "x,  ,y,x")
}
