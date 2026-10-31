package dev.ashtonite365.apex.test.features.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.world.SoulAStar
import dev.ashtonite365.apex.features.world.SoulAStar.P

class SoulAStarTest {

	class G : SoulAStar.Grid {
		val m = HashMap<P, Byte>()
		fun set(x: Int, y: Int, z: Int, c: Byte) { m[P(x, y, z)] = c }
		override fun cell(x: Int, y: Int, z: Int): Byte = when {
			x !in -20..20 || z !in -20..20 || y !in -20..30 -> SoulAStar.BLOCKED
			else -> m[P(x, y, z)] ?: if (y < 0) SoulAStar.SOLID else SoulAStar.AIR
		}
	}

	private fun valid(g: G, path: List<P>) = path.all { SoulAStar.standable(g, it.x, it.y, it.z) }

	@Test
	fun flatStraight() {
		val p = SoulAStar.find(G(), P(0, 0, 0), P(5, 0, 0))!!
		assertEquals(P(0, 0, 0), p.first())
		assertTrue(p.size in 4..7)
	}

	@Test
	fun routesAroundWall() {
		val g = G()
		for (z in -5..5) for (y in 0..3) g.set(2, y, z, SoulAStar.SOLID)
		val p = SoulAStar.find(g, P(0, 0, 0), P(4, 0, 0))!!
		assertTrue(valid(g, p))
		assertTrue(p.any { it.z !in -5..5 }, "must go around the wall ends")
		assertTrue(p.none { it.x == 2 && it.z in -5..5 })
	}

	@Test
	fun stepUpOneButNotTwo() {
		val g = G()
		for (x in 2..6) for (z in -20..20) g.set(x, 0, z, SoulAStar.SOLID)
		val p = SoulAStar.find(g, P(0, 0, 0), P(4, 1, 0))!!
		assertTrue(p.zipWithNext().all { (a, b) -> b.y - a.y <= 1 })
		val g2 = G()
		for (x in 2..6) for (z in -20..20) { g2.set(x, 0, z, SoulAStar.SOLID); g2.set(x, 1, z, SoulAStar.SOLID) }
		assertNull(SoulAStar.find(g2, P(0, 0, 0), P(4, 2, 0)))
	}

	@Test
	fun noDropLimit() {
		val g = G()

		for (x in -20..0) for (z in -20..20) for (y in 0..19) g.set(x, y, z, SoulAStar.SOLID)
		assertNotNull(SoulAStar.find(g, P(0, 20, 0), P(4, 0, 0)))
	}

	@Test
	fun stairsWalkableAndClimbable() {
		val g = G()

		for (i in 1..4) {
			for (z in -20..20) {
				for (y in 0 until i - 1) g.set(i, y, z, SoulAStar.SOLID)
				g.set(i, i - 1, z, SoulAStar.STAIR)
			}
		}
		for (x in 5..20) for (z in -20..20) for (y in 0..3) g.set(x, y, z, SoulAStar.SOLID)
		for (x in -20..20) for (z in -20..20) { val top = maxOf(0, minOf(x, 4)) + 2; if (g.m[P(x, top, z)] == null) g.set(x, top, z, SoulAStar.SOLID) }
		val p = SoulAStar.find(g, P(0, 0, 0), P(7, 4, 0))!!
		assertTrue(valid(g, p))
		assertTrue(p.any { it.x == 2 && it.y == 2 }, "walks on the stairs: $p")

		g.m.keys.filter { g.m[it] == SoulAStar.STAIR }.forEach { g.m[it] = SoulAStar.SOLID }
		assertNull(SoulAStar.find(g, P(0, 0, 0), P(7, 4, 0)))
	}

	@Test
	fun upwardLimitGrowsOnRetry() {
		val g = G()
		for (x in 2..20) for (z in -20..20) for (y in 0..9) g.set(x, y, z, SoulAStar.SOLID)
		for (y in 0..9) g.set(1, y, 0, SoulAStar.CLIMB)
		assertNull(SoulAStar.find(g, P(0, 0, 0), P(4, 10, 0), maxUp = SoulAStar.UP_LIMIT_START))
		assertNull(SoulAStar.find(g, P(0, 0, 0), P(4, 10, 0), maxUp = 8))
		assertNotNull(SoulAStar.find(g, P(0, 0, 0), P(4, 10, 0), maxUp = 10))
		assertNotNull(SoulAStar.findWithRetries(g, P(0, 0, 0), P(4, 10, 0)))
	}

	@Test
	fun ladderClimb() {
		val g = G()
		for (x in 2..20) for (z in -20..20) for (y in 0..5) g.set(x, y, z, SoulAStar.SOLID)
		for (y in 0..5) g.set(1, y, 0, SoulAStar.CLIMB)
		val p = SoulAStar.find(g, P(0, 0, 0), P(4, 6, 0))!!
		assertTrue(p.any { it.x == 1 && it.y >= 3 }, "climbs the ladder")
		g.m.keys.filter { g.m[it] == SoulAStar.CLIMB }.forEach { g.m.remove(it) }
		assertNull(SoulAStar.find(g, P(0, 0, 0), P(4, 6, 0)))
	}

	@Test
	fun nodeCapAndDistanceCap() {
		val g = G()
		for (z in -20..20) for (y in 0..3) g.set(10, y, z, SoulAStar.SOLID)
		assertNull(SoulAStar.find(g, P(0, 0, 0), P(15, 0, 0), nodeCap = 50))
		assertNull(SoulAStar.find(G(), P(0, 0, 0), P(15, 0, 0), maxDist = 5))
	}

	@Test
	fun noCornerCutting() {
		val g = G()
		for (y in 0..1) { g.set(1, y, 0, SoulAStar.SOLID); g.set(0, y, 1, SoulAStar.SOLID) }
		val out = ArrayList<P>()
		SoulAStar.neighbours(g, P(0, 0, 0), out)
		assertTrue(P(1, 0, 1) !in out)

		val g2 = G()
		for (y in 0..1) g2.set(1, y, 0, SoulAStar.SOLID)
		val out2 = ArrayList<P>()
		SoulAStar.neighbours(g2, P(0, 0, 0), out2)
		assertTrue(P(1, 0, 1) !in out2)
		val out3 = ArrayList<P>()
		SoulAStar.neighbours(G(), P(0, 0, 0), out3)
		assertTrue(P(1, 0, 1) in out3)
	}

	@Test
	fun simplifyCollapsesRuns() {
		val path = (0..5).map { P(it, 0, 0) } + P(5, 0, 1)
		assertEquals(listOf(P(0, 0, 0), P(5, 0, 0), P(5, 0, 1)), SoulAStar.simplify(path))
	}
}
