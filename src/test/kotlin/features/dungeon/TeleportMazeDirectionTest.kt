package dev.ashtonite365.apex.test.features.dungeon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.TeleportMaze
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Guide
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.Pad
import dev.ashtonite365.apex.features.dungeon.TeleportMaze.State
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class TeleportMazeDirectionTest {
	private val cx = -168; private val cz = -136
	private val kitLocal = listOf(
		4 to 12, 4 to 6, 10 to 12, 10 to 6, 4 to 20, 4 to 14, 10 to 20, 10 to 14,
		4 to 28, 4 to 22, 10 to 28, 10 to 22, 12 to 28, 12 to 22, 18 to 28, 18 to 22,
		20 to 28, 20 to 22, 26 to 28, 26 to 22, 26 to 20, 26 to 14, 20 to 20, 20 to 14,
		26 to 12, 26 to 6, 20 to 12, 20 to 6, 15 to 14, 15 to 12,
	)
	private fun rotB(x: Int, z: Int, k: Int): Pair<Int, Int> { var a = x; var b = z; repeat(k) { val n = 30 - b; b = a; a = n }; return a to b }
	private fun rotD(x: Double, z: Double, k: Int): Pair<Double, Double> { var a = x; var b = z; repeat(k) { val n = 31.0 - b; b = a; a = n }; return a to b }
	private fun unrot(p: Pad, k: Int): Pair<Int, Int> { var a = p.x - cx; var b = p.z - cz; repeat(k) { val n = b; b = 30 - a; a = n }; return a to b }
	private fun pad(x: Int, z: Int, k: Int) = rotB(x, z, k).let { Pad(cx + it.first, 69, cz + it.second) }
	private fun kit(k: Int) = kitLocal.map { pad(it.first, it.second, k) }.toSet()
	private val room = TeleportMaze.dungeonRoom(cx + 5, cz + 5)

	@Test fun yawToMatchesMinecraftFacing() {

		assertEquals(0.0, TeleportMaze.yawTo(0.0, 1.0), 1e-9)
		assertEquals(90.0, TeleportMaze.yawTo(-1.0, 0.0), 1e-9)
		assertEquals(180.0, abs(TeleportMaze.yawTo(0.0, -1.0)), 1e-9)
		assertEquals(-90.0, TeleportMaze.yawTo(1.0, 0.0), 1e-9)

		for (yaw in listOf(-170.0, -135.0, -20.0, 0.0, 33.0, 90.0, 179.0)) {
			val r = Math.toRadians(yaw)
			assertEquals(0.0, ((TeleportMaze.yawTo(-sin(r), cos(r)) - yaw) % 360.0 + 540.0) % 360.0 - 180.0, 1e-9)
		}
	}

	@Test fun yawDiffWrapsAtTheNorthSeam() {

		val p = Pad(-1, 70, -10)
		assertTrue(TeleportMaze.yawDiff(p, 0.5, 0.5, 180f) < 7.0)
		assertTrue(TeleportMaze.yawDiff(p, 0.5, 0.5, -180f) < 7.0)
		assertTrue(TeleportMaze.yawDiff(Pad(0, 70, 10), 0.5, 0.5, 180f) > 179.0)
		assertTrue(TeleportMaze.yawDiff(Pad(0, 70, -10), 0.5, 0.5, 540f) < 1e-6)
	}

	@Test fun originPadFloorsNegativeCoordinates() {

		assertEquals(Pad(-151, 69, -121), TeleportMaze.originPad(-150.5, 69.5, -120.2))
		assertNotEquals(Pad((-150.5).toInt(), 69, (-120.2).toInt()), TeleportMaze.originPad(-150.5, 69.5, -120.2))
		assertEquals(Pad(3, 69, 7), TeleportMaze.originPad(3.9, 69.0, 7.1))
	}

	private fun cellGuide(k: Int, yawLocal: Float?, likely: Set<Pad>? = null): Guide.Step {
		val all = kit(k)
		val here = pad(20, 22, k)
		val visited = setOf(pad(15, 12, k), here)
		val links = mapOf(pad(15, 12, k) to here, here to pad(15, 12, k))
		val yaw = yawLocal?.let { it + 90f * k }
		return TeleportMaze.guide(all, visited, links, here, emptySet(), likely, pad(15, 12, k), yaw, pad(15, 12, k)) as Guide.Step
	}

	@Test fun guideFollowsTheArrivalFacingInEveryRotation() {
		for (k in 0..3) {

			for (off in listOf(-8f, 0f, 8f)) {
				val toZ = cellGuide(k, 0f + off)
				assertEquals(pad(20, 28, k), toZ.target, "k=$k off=$off facing (20,28)"); assertEquals(toZ.target, toZ.next)
				assertEquals(0, toZ.teleports); assertEquals(Guide.Why.UNKNOWN, toZ.why)
				val toX = cellGuide(k, -90f + off)
				assertEquals(pad(26, 22, k), toX.target, "k=$k off=$off facing (26,22)")
			}
		}
	}

	@Test fun guideIsNearestUntriedThenPrefersLikely() {
		for (k in 0..3) {

			val g = cellGuide(k, TeleportMaze.yawTo(6.0, 6.0).toFloat())
			assertTrue(g.target == pad(20, 28, k) || g.target == pad(26, 22, k), "k=$k")
			assertEquals(6.0, g.walk, 1e-9)

			val l = cellGuide(k, null, setOf(pad(26, 28, k)))
			assertEquals(pad(26, 28, k), l.target); assertEquals(Guide.Why.LIKELY, l.why)
		}
	}

	@Test fun guideNeverPointsBackToTheStart() {
		for (k in 0..3) {
			val g = cellGuide(k, 180f)
			assertNotEquals(pad(20, 22, k), g.next, "k=$k: the pad back to the start is never the step")
			assertNotEquals(pad(15, 12, k), g.target); assertNotEquals(pad(15, 14, k), g.target)
		}
	}

	@Test fun arrowHeadsPointAtTheDestinationInEveryRotation() {
		for (k in 0..3) for ((a, b) in listOf((4 to 6) to (20 to 14), (26 to 28) to (10 to 12), (20 to 22) to (20 to 28))) {
			val (ax, az) = rotD(a.first + 0.5, a.second + 0.5, k); val (bx, bz) = rotD(b.first + 0.5, b.second + 0.5, k)
			val h = TeleportMaze.arrowHead(ax, az, bx, bz)!!
			val (tipX, tipZ, baseX, baseZ) = h.toList()
			val dot = (tipX - baseX) * (bx - ax) + (tipZ - baseZ) * (bz - az)
			assertTrue(dot > 0, "k=$k $a->$b: arrowhead points from the source to the destination")
			assertTrue((tipX - bx) * (tipX - bx) + (tipZ - bz) * (tipZ - bz) < (baseX - bx) * (baseX - bx) + (baseZ - bz) * (baseZ - bz))
		}
	}

	@Test fun middlePadsAreStartInEveryRotation() {
		for (k in 0..3) {
			val all = kit(k)
			assertEquals(setOf(pad(15, 12, k), pad(15, 14, k)), TeleportMaze.startPads(all), "k=$k")
			val st = TeleportMaze.states(all, mapOf(pad(15, 12, k) to pad(4, 6, k), pad(4, 6, k) to pad(15, 12, k)), emptySet(), pad(15, 12, k), null)
			assertEquals(State.START, st[pad(15, 12, k)]); assertEquals(State.BACK_START, st[pad(4, 6, k)])
			assertFalse(st.values.any { it == State.LEAVE_DEAD_END })
		}
	}

	private fun two(vararg p: Pair<Pad, Pad>) = buildMap { for ((a, b) in p) { put(a, b); put(b, a) } }

	@Test fun backToPreviousRoomIsNotADeadEnd() {
		for (k in 0..3) {
			fun q(x: Int, z: Int) = pad(x, z, k)

			val links = two(q(15, 12) to q(4, 6), q(10, 6) to q(20, 22), q(4, 12) to q(26, 6), q(10, 12) to q(26, 12))
			val st = TeleportMaze.states(kit(k), links, emptySet(), q(15, 12), null, previous = q(10, 6))
			assertEquals(State.BACK_PREVIOUS, st[q(20, 22)], "k=$k")

			val noPrev = TeleportMaze.states(kit(k), links, emptySet(), q(15, 12), null)
			assertNotEquals(State.LEAVE_DEAD_END, noPrev[q(20, 22)])
			assertFalse(noPrev.values.any { it == State.LEAVE_DEAD_END })
		}
	}

	@Test fun genuineNoExitRoomIsADeadEnd() {
		for (k in 0..3) {
			fun q(x: Int, z: Int) = pad(x, z, k)

			val links = two(q(15, 12) to q(4, 6), q(10, 6) to q(20, 22), q(26, 22) to q(4, 14))
			val exits = setOf(q(4, 20), q(10, 20), q(10, 14))
			val st = TeleportMaze.states(kit(k), links, exits, q(15, 12), null, previous = q(10, 6))
			assertEquals(State.LEAVE_DEAD_END, st[q(26, 22)], "k=$k")
			assertEquals(State.BACK_PREVIOUS, st[q(20, 22)])
			val g = TeleportMaze.guide(kit(k), setOf(q(15, 12), q(4, 6), q(10, 6), q(20, 22), q(26, 22), q(4, 14)), links, q(20, 22), exits, null, q(15, 12), null, q(10, 6)) as Guide.Step
			assertNotEquals(q(26, 22), g.next); assertNotEquals(q(20, 22), g.next); assertEquals(0, g.teleports)

			assertNotEquals(State.LEAVE_DEAD_END, TeleportMaze.states(kit(k), links, exits - q(10, 14), q(15, 12), null)[q(26, 22)])
		}
	}

	private data class Step(val on: Pair<Int, Int>, val x: Double, val z: Double, val yaw: Float)
	private val seqs = listOf(
		listOf(Step(4 to 28, 3.5, 6.5, -90f), Step(10 to 6, 21.5, 22.5, 180f)),
		listOf(Step(4 to 6, 21.5, 14.5, 0f), Step(26 to 20, 16.5, 12.5, 90f)),
		listOf(Step(10 to 20, 5.5, 14.5, 135f), Step(4 to 20, 19.5, 6.5, -150f), Step(26 to 12, 11.5, 22.5, 170f)),
		listOf(Step(10 to 14, 25.5, 27.5, -170f), Step(20 to 28, 4.5, 11.5, 45f), Step(4 to 6, 27.5, 21.5, 100f)),
	)
	private fun fmt(s: Collection<Pad>?, k: Int) = s?.map { unrot(it, k) }?.sortedWith(compareBy({ it.first }, { it.second }))?.joinToString(",") { "${it.first}:${it.second}" } ?: "null"

	private fun predictionDump(): List<String> {
		val out = mutableListOf<String>()
		for (k in 0..3) {
			val all = kit(k)
			for ((i, seq) in seqs.withIndex()) {
				val t = TeleportMaze.Tracker().apply { setRoomPads(all, room) }
				for ((j, s) in seq.withIndex()) {
					val p = pad(s.on.first, s.on.second, k); t.tick(p.x + 0.5, 69.8125, p.z + 0.5, p, false)
					val (wx, wz) = rotD(s.x, s.z, k)
					t.tick(cx + wx, 69.5, cz + wz, null, true, s.yaw + 90f * k)
					out += "k=$k seq=$i step=$j likely=${fmt(t.likely, k)}"
				}
				val st = TeleportMaze.states(t.allPads, t.links, t.exits, t.firstPad, t.likely, t.previous)
				out += "k=$k seq=$i likelyState=${fmt(st.filterValues { it == State.LIKELY }.keys, k)}"
			}
			for (o in listOf(5.5 to 6.5, 21.5 to 22.5, 15.5 to 2.5, 11.5 to 27.5)) for (yaw in listOf(-135f, -90f, -45f, 0f, 45f, 90f, 135f, 180f, -170f, 10f)) {
				val (wx, wz) = rotD(o.first, o.second, k)
				out += "k=$k ray o=${o.first}:${o.second} yaw=$yaw hits=${fmt(all.filter { TeleportMaze.rayHits(it, cx + wx, cz + wz, yaw + 90f * k, 0f) }, k)}"
			}
		}
		return out
	}

	@Test fun likelyPredictionUnchangedOnFixtures() {
		val base = javaClass.getResourceAsStream("/tpmaze/prediction-baseline-fee27bd9.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }
		val expected = base.map { line ->
			if (" states=" !in line) line
			else line.substringBefore(" states=") + " likelyState=" + line.substringAfter(" states=").split(",")
				.filter { it.endsWith("=LIKELY") }.joinToString(",") { it.substringBefore("=") }
		}
		assertEquals(216, expected.size)
		assertEquals(expected, predictionDump())
	}
}
