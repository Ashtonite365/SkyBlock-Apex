// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.mining.DwarvenMinesWaypoints
import dev.ashtonite365.apex.features.mining.MetalDetectorSolver
import dev.ashtonite365.apex.features.mining.MetalDetectorSolver.Outcome
import dev.ashtonite365.apex.features.mining.WishingCompassSolver
import dev.ashtonite365.apex.features.mining.WishingCompassSolver.CompassTarget
import dev.ashtonite365.apex.features.mining.WishingCompassSolver.HollowsZone
import dev.ashtonite365.apex.features.mining.WishingCompassSolver.Ray
import dev.ashtonite365.apex.features.mining.WishingCompassSolver.Step
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch7GoldenTest {
	private fun code(o: Outcome) = when (o) {
		Outcome.Unsteady -> "U"
		Outcome.Dug -> "D"
		Outcome.Lost -> "L"
		is Outcome.Narrowed -> "N${o.left}"
		is Outcome.Solved -> "S" + o.spot.toShortString()
	}

	@Test
	fun mining() {
		val metal by lazy { Batch7Corpus.metalCases(MetalDetectorSolver.knownChestOffsets.toList()) }
		val points by lazy { Batch7Corpus.compassPoints() }
		val runs by lazy { Batch7Corpus.compassRuns() }
		val dwarven by lazy { Batch7Corpus.dwarvenCases() }
		val crystals = Batch7Corpus.crystals
		val rays by lazy {
			runs.take(60).flatMap { run ->
				run.filter { it.second != null }.map { it.second!! }.chunked(4).filter { it.size == 4 }
					.map { Ray(it.first(), it.last().subtract(it.first()).let { d -> if (d.lengthSqr() < 1e-9) d else d.normalize() }) }
					.let { rs -> rs.flatMap { a -> rs.map { b -> a to b } } }
			}
		}
		var meetIndex = 0
		val solved = intArrayOf(0)
		val out = Golden.load("batch7-mining").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"bar" -> MetalDetectorSolver.readDistance(k.substringAfter('|')).toString()
					"metal" -> {
						val case = metal[p[1].toInt()]
						val s = case.session
						val n = MetalDetectorSolver.Narrowing()
						val codes = s.steps.map { (eye, d) -> code(n.feed(eye, d, s.centre) { s.solidAt(it, case.treasures) }) }
						if (codes.any { it.startsWith("S") }) solved[0]++
						codes.joinToString(" ") + " | " + n.spots.joinToString(";") { it.toShortString() }
					}
					"offsets" -> MetalDetectorSolver.knownChestOffsets.joinToString(";") { "${it.x},${it.y},${it.z}" }
					"keepers" -> MetalDetectorSolver.keeperOffsets.entries.joinToString(";") { "${it.key}=${it.value.x},${it.value.y},${it.value.z}" }
					"cand" -> {
						val mask = p[1].toInt()
						WishingCompassSolver.possibleTargets({ mask shr crystals.indexOf(it) and 1 == 1 }, p[2].toBoolean(), p[3].toBoolean()).joinToString("+")
					}
					"zone" -> WishingCompassSolver.zoneFor(points[p[1].toInt()]).toString()
					"filter" -> {
						val set = CompassTarget.entries.filterIndexed { b, _ -> p[2].toInt() shr b and 1 == 1 }.toSet()
						WishingCompassSolver.narrowTargets(set, points[p[1].toInt()], p[3].toBoolean(), HollowsZone.valueOf(p[4])).joinToString("+")
					}
					"run" -> {
						val t = WishingCompassSolver.Triangulation()
						val events = mutableListOf<String>()
						var uses = 0
						for ((time, at) in runs[p[1].toInt()]) {
							val step = if (at == null) {
								val zone = HollowsZone.entries[uses++ % 6]
								t.use(time) { zone }
							} else t.particle(time, at)
							when (step) {
								null -> {}
								Step.FirstLine -> events += "first"
								Step.Parallel -> events += "parallel"
								is Step.Point -> events += "point ${Batch7Corpus.f(step.at)} " +
									WishingCompassSolver.narrowTargets(WishingCompassSolver.possibleTargets({ false }, false, false), step.at, false, step.startZone).joinToString("+")
							}
						}
						events.joinToString(" / ") + " | " + (t.point?.let(Batch7Corpus::f) ?: "-")
					}
					"meet" -> rays[meetIndex++].let { (a, b) -> WishingCompassSolver.intersect(a, b)?.let(Batch7Corpus::f) ?: "null" }
					"chat" -> DwarvenMinesWaypoints.announcement(k.split('|', limit = 4)[3], p[1].toBoolean(), p[2].toBoolean())
						?.let { "${it.label}@${it.area}" } ?: "-"
					"comm" -> DwarvenMinesWaypoints.parseCommissions(dwarven[p[1].toInt()].commissions).joinToString(";") { "${it.first}=${it.second}" }
					"marks" -> {
						val c = dwarven[p[1].toInt()]
						DwarvenMinesWaypoints.markers(DwarvenMinesWaypoints.parseCommissions(c.commissions), c.here,
							DwarvenMinesWaypoints.LocationMode.entries[c.mode], c.hide, c.emissaries,
							c.live?.let { DwarvenMinesWaypoints.Announcement(it.first, it.second) }).joinToString(";") { "${it.first.toShortString()}=${it.second}" }
					}
					else -> error(k)
				}
			}
		}
		Golden.verify("batch7-mining", out, minCases = 8900)
		assertTrue(solved[0] > 20, "metal sessions that reach a single spot: ${solved[0]}")
	}
}
