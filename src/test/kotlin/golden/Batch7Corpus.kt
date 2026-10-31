// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import kotlin.math.floor
import kotlin.random.Random
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.world.phys.Vec3

object Batch7Corpus {

	class MetalSession(val centre: BlockPos?, val seed: Int, val steps: List<Pair<Vec3, Double>>) {

		fun solidAt(pos: BlockPos, treasures: Set<BlockPos>): Boolean {
			if (pos in treasures) return true
			if (pos.below() in treasures) return false
			val h = (pos.x * 73856093) xor (pos.y * 19349663) xor (pos.z * 83492791) xor seed
			return Math.floorMod(h, 3) != 0
		}
	}

	class MetalCase(val session: MetalSession, val treasures: Set<BlockPos>)

	private fun top(p: BlockPos) = Vec3(p.x.toDouble(), p.y + 1.0, p.z.toDouble())
	private fun reading(eye: Vec3, p: BlockPos) = Math.rint(eye.distanceTo(top(p)) * 10) / 10

	fun metalCases(offsets: List<Vec3i>): List<MetalCase> {
		val r = Random(81)
		return List(260) { i ->
			val withCentre = i % 3 != 0
			val centre = if (withCentre) BlockPos(r.nextInt(400, 700), 100 + r.nextInt(-3, 4) * 0, r.nextInt(400, 700)) else null
			fun pickTreasure(): BlockPos = if (centre != null) centre.offset(offsets[r.nextInt(offsets.size)])
			else BlockPos(r.nextInt(-200, 200), r.nextInt(65, 76), r.nextInt(-200, 200))
			var treasure = pickTreasure()
			val treasures = linkedSetOf(treasure)
			val steps = mutableListOf<Pair<Vec3, Double>>()
			var eye = Vec3(treasure.x + r.nextDouble(-12.0, 12.0), treasure.y + r.nextDouble(1.0, 8.0), treasure.z + r.nextDouble(-12.0, 12.0))
			repeat(r.nextInt(3, 14)) {
				when (r.nextInt(10)) {
					0, 1 -> eye = Vec3(treasure.x + r.nextDouble(-15.0, 15.0), treasure.y + r.nextDouble(0.0, 9.0), treasure.z + r.nextDouble(-15.0, 15.0))
					2 -> eye = eye.add(r.nextDouble(-0.05, 0.05), 0.0, r.nextDouble(-0.05, 0.05))
					3 -> if (r.nextInt(4) == 0) { treasure = pickTreasure(); treasures += treasure }
					else -> {}
				}
				val noise = when (r.nextInt(12)) { 0 -> 0.1; 1 -> -0.3; 2 -> 5.0; else -> 0.0 }
				steps += eye to (reading(eye, treasure) + noise)
				if (r.nextInt(3) == 0) steps += eye to reading(eye, treasure)
			}
			MetalCase(MetalSession(centre, r.nextInt(), steps), treasures)
		}
	}

	val actionBars = listOf("TREASURE: 12.3m", "§aTREASURE: §b4.0m", "TREASURE: m", "TREASURE: 1.2.3m", "TREASURE: 7m away", "treasure: 3m",
		"TREASURE: ١٢m", "x TREASURE: 9.95m", "TREASURE: 3.5", "TREASURE:3.5m", "", "TREASURE: 0.0m", "TREASURE: -4m", "TREASURE: 4,5m")

	val crystals = listOf("Jade", "Amber", "Topaz", "Amethyst", "Sapphire")

	fun compassPoints(): List<Vec3> {
		val r = Random(82)
		val fixed = listOf(Vec3(512.0, 100.0, 512.0), Vec3(462.0, 63.0, 461.0), Vec3(564.0, 181.0, 565.0), Vec3(513.0, 64.0, 513.0),
			Vec3(200.0, 70.0, 200.0), Vec3(824.0, 189.0, 824.0), Vec3(700.0, 30.0, 300.0), Vec3(300.0, 75.0, 300.0))
		return fixed + List(900) { Vec3(r.nextDouble(150.0, 880.0), r.nextDouble(0.0, 200.0), r.nextDouble(150.0, 880.0)) } +
			List(300) { Vec3(r.nextInt(150, 880).toDouble(), r.nextInt(0, 200).toDouble(), r.nextInt(150, 880).toDouble()) }
	}

	fun compassRuns(): List<List<Pair<Long, Vec3?>>> {
		val r = Random(83)
		return List(220) {
			var t = 1_000_000L
			buildList {
				repeat(r.nextInt(2, 6)) {
					t += r.nextLong(500, 9000)
					add(t to null)
					val origin = Vec3(r.nextDouble(200.0, 800.0), r.nextDouble(40.0, 180.0), r.nextDouble(200.0, 800.0))
					val dir = when (r.nextInt(8)) {
						0 -> Vec3(1.0, 0.0, 0.0)
						1 -> Vec3(0.0, 0.0, 0.0)
						else -> Vec3(r.nextDouble(-1.0, 1.0), r.nextDouble(-0.4, 0.4), r.nextDouble(-1.0, 1.0))
					}
					repeat(r.nextInt(0, 11)) { k ->
						t += r.nextLong(20, if (r.nextInt(10) == 0) 4000 else 300)
						add(t to origin.add(dir.scale(k * 0.5)).add(r.nextDouble(-0.01, 0.01), 0.0, 0.0))
					}
				}
			}
		}
	}

	val chatLines = listOf("Find the Powder Ghast near the Royal Mines!", "§cFind the Powder Ghast near the  Cliffside Veins !",
		"Find the Powder Ghast near the !", "Find the Powder Ghast near the Forge!!", "xx Find the Powder Ghast near the The Mist! yes!",
		"Find the Powder Ghast near the Lava Springs\nlater!", "Find the Powder Ghast near the A!\nFind the Powder Ghast near the B!",
		"Fallen Star has crashed at Royal Palace!", "✯ A Fallen Star has crashed at Forge Basin! Nearby players get a bonus!",
		"Fallen Star has crashed at !Upper Mines!", "Fallen Star has crashed at", "Find the Powder Ghast near the Royal Mines",
		"Find the Powder Ghast near the Goblin Burrows! Fallen Star has crashed at Far Reserve!", "", "hello",
		"Find the Powder Ghast near the Nowhere!", "Find the Powder Ghast near the Royal Mines!\u2028!", "Fallen Star has crashed at Dwarven Village!\r")

	val areaNames = listOf("Dwarven Village", "Miner's Guild", "Royal Mines", "The Forge", "Forge Basin", "Cliffside Veins", "Goblin Burrows",
		"Far Reserve", "Upper Mines", "Lava Springs", "Rampart's Quarry", "Divan's Gateway", "Nowhere", "The Mist", "Royal Palace")

	class DwarvenCase(val commissions: List<String>, val here: String?, val mode: Int, val hide: Boolean, val emissaries: Boolean,
		val live: Pair<String, String>?)

	fun dwarvenCases(): List<DwarvenCase> {
		val r = Random(84)
		val kinds = listOf("Titanium", "Mithril", "Goblin Slayer", "Ice Walker Slayer", "Star Sentry Puncher")
		val progress = listOf("DONE", "50%", "0%", "12.5%", "")
		return List(600) {
			val comms = List(r.nextInt(0, 5)) {
				when (r.nextInt(6)) {
					0 -> "no colon here"
					1 -> "  ${kinds.random(r)}: ${progress.random(r)}  "
					else -> "${if (r.nextBoolean()) areaNames.random(r) else areaNames.random(r).lowercase()} ${kinds.random(r)}: ${progress.random(r)}"
				}
			}
			val here = when (r.nextInt(4)) { 0 -> null; 1 -> areaNames.random(r).replace("'", ""); else -> areaNames.random(r) }
			val live = if (r.nextBoolean()) (if (r.nextBoolean()) "§6Powder Ghast" else "§5Fallen Star") to areaNames.random(r) else null
			DwarvenCase(comms, here, r.nextInt(3), r.nextBoolean(), r.nextBoolean(), live)
		}
	}

	fun f(v: Vec3) = String.format(java.util.Locale.ROOT, "%.6f,%.6f,%.6f", v.x, v.y, v.z)
	fun floorPos(v: Vec3) = BlockPos(floor(v.x).toInt(), floor(v.y).toInt(), floor(v.z).toInt())
}
