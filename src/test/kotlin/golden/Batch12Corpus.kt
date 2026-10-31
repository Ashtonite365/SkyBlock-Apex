// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object Batch12Garden {
	private val tabPool = listOf("Farming Fortune: ☘250", " Farming Fortune: 1,000", "  Farming Fortune: ☘0", "Farming Fortune: ☘99999999999999999999",
		"Wheat Fortune: ☘45", " Sugar Cane Fortune: ☘120", "Cocoa Beans Fortune: 30", "Nether Wart Fortune: ☘7", "wheat fortune: ☘3",
		"Melon Slice Fortune: ☘80", "Melon Fortune: ☘81", "Mushroom Fortune: ☘15 ", "§6Farming Fortune: §f☘300", "Farming Fortune: ☘١٢",
		" Next Visitor: 1m 30s", "Next Visitor: §eQueue Full!", "Next Visitor:  Now ", "Next visitor: 5s", "Next Visitor: 2m\n", "Pests: 3", "")
	val tabs: List<List<String>> by lazy { val r = Random(1201); List(400) { List(r.nextInt(0, 7)) { tabPool.random(r) } } }
	val ffs = listOf(0.0, 1.0, 100.0, 250.5, -100.0, 1e6, Double.NaN, 1999.99)
	val bps = listOf(20.0, 19.5, 0.0, 1e-3)
	val floats = listOf(0f, 1.23456f, -45.5f, 179.99f, -180f, 360.25f, 1e7f, Float.NaN, -0.0f, 2.8f, 0.004999f)
	val precisions = listOf(-1, 0, 1, 2, 4, 9)
	val yawPairs: List<Pair<Float, Float>> by lazy { val r = Random(1202); List(300) { (r.nextFloat() * 1440f - 720f) to listOf(-90f, 180f, 116.5f, -135f, 90f).random(r) } }
	val divisors = listOf(-3, 0, 1, 2, 4, 20)
	val sensitivities = listOf(0.0, 0.5, 1.0, 0.123456789, 2.0)

	private val rewardPool = listOf("Rewards:", "+2 Farming XP", " Flowering Bouquet", "VIP Voter's Badge", "Voter's Badge x2", "Copper Dye", "Space Helmet!",
		"Overgrown Grass and Green Bandana", "Dedication IV", "Dedication I", "Supreme Voter's Badge", "§9Music Rune", "nothing rare", "", "Jelly Garden Greenhouse Skin")
	val lores: List<List<String>> by lazy { val r = Random(1203); List(300) { List(r.nextInt(0, 6)) { rewardPool.random(r) } } }

	private val costPool = listOf("Cost", " Cost ", "cost", "2x Gold medals", "Silver medal x3", "bronze medal", "1,000 Copper", "  25 Copper  ", "Copper 5", "5x Gold Medals ",
		"GOLD MEDAL", "3 silver medals", "Bronze medal x1,0", "", "Click to trade!", "12 Copper", "x Gold medal", "99999999999 Copper", "Diamond medal")
	val costLores: List<List<String>> by lazy { val r = Random(1204); List(400) { buildList { repeat(r.nextInt(0, 2)) { add("Lore") }; if (r.nextInt(5) > 0) add("Cost"); repeat(r.nextInt(0, 5)) { add(costPool.random(r)) } } } }

	val trails: List<List<Vec3>> by lazy {
		val r = Random(1205)
		List(300) { k ->
			val start = Vec3(r.nextDouble(-100.0, 100.0), r.nextDouble(60.0, 120.0), r.nextDouble(-100.0, 100.0))
			val pitch = Math.toRadians(r.nextDouble(-89.0, 89.0)); val yaw = Math.toRadians(r.nextDouble(0.0, 360.0))
			val look = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
			val p1 = start.add(look.scale(4.0)).add(0.0, 3.0, 0.0)
			val pest = start.add(r.nextDouble(-40.0, 40.0), r.nextDouble(-10.0, 10.0), r.nextDouble(-40.0, 40.0))
			val p2 = pest.add(r.nextDouble(-5.0, 5.0), r.nextDouble(-5.0, 5.0), r.nextDouble(-5.0, 5.0))
			val total = r.nextInt(8, 60); val shown = when (k % 10) { 0 -> r.nextInt(0, 4); 1 -> total + 1; else -> r.nextInt(4, total + 1) }
			val noise = if (k % 3 == 0) 0.0 else r.nextDouble(0.0, 0.05)
			List(shown) { i ->
				val s = i.toDouble() / total; val u = 1 - s
				val b = start.scale(u * u * u).add(p1.scale(3 * u * u * s)).add(p2.scale(3 * u * s * s)).add(pest.scale(s * s * s))
				if (k % 25 == 7) start else if (noise == 0.0) b else b.add(r.nextDouble(-noise, noise), r.nextDouble(-noise, noise), r.nextDouble(-noise, noise))
			}
		}
	}
	val slopes = listOf(-3.0, -1.5, -0.6435, 0.0, 0.5, 1.0, 1.5707, 3.0, Double.NaN)
	val dirs = listOf(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, -1.0, 0.0), Vec3(1.0, -1.0, 1.0), Vec3(0.0, 0.0, 0.0), Vec3(-3.0, 2.0, 0.5))
	fun v(p: Vec3?) = p?.let { "%.9f,%.9f,%.9f".format(java.util.Locale.ROOT, it.x, it.y, it.z) } ?: "null"
}
