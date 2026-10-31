package dev.ashtonite365.apex.test.features.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.world.SoulAStar
import dev.ashtonite365.apex.features.world.SoulAStar.P

class ThinBlockPathTest {
	private fun px(v: Int) = v / 16.0
	private fun shape(x0: Int, x1: Int, y0: Int, y1: Int, z0: Int, z1: Int) =
		SoulAStar.classifyShape(px(x0), px(x1), px(y0), px(y1), px(z0), px(z1))

	@Test fun shapes() {
		assertEquals(SoulAStar.AIR, shape(6, 10, 0, 16, 6, 10), "vertical chain (6.5..9.5 px)")
		assertEquals(SoulAStar.AIR, shape(0, 16, 6, 10, 6, 10), "chain along x")
		assertEquals(SoulAStar.AIR, shape(6, 10, 6, 10, 0, 16), "chain along z")
		assertEquals(SoulAStar.AIR, shape(6, 10, 0, 16, 6, 10), "end rod / lightning rod")
		assertEquals(SoulAStar.AIR, shape(5, 11, 0, 9, 5, 11), "lantern")
		assertEquals(SoulAStar.AIR, shape(0, 16, 0, 1, 0, 16), "carpet")
		assertEquals(SoulAStar.SOLID, shape(6, 10, 0, 24, 6, 10), "fence post (1.5 high)")
		assertEquals(SoulAStar.SOLID, shape(4, 12, 0, 24, 4, 12), "wall post")
		assertEquals(SoulAStar.SOLID, shape(0, 16, 0, 16, 7, 9), "glass pane / iron bars joined east-west")
		assertEquals(SoulAStar.SOLID, shape(0, 16, 0, 8, 0, 16), "bottom slab")
		assertEquals(SoulAStar.SOLID, shape(0, 16, 13, 16, 0, 16), "top trapdoor")
		assertEquals(SoulAStar.SOLID, shape(0, 16, 0, 16, 0, 16), "full block")
	}

	private class G(val gapCell: Byte) : SoulAStar.Grid {

		override fun cell(x: Int, y: Int, z: Int): Byte = when {
			x !in -20..20 || z !in -20..20 || y !in -20..30 -> SoulAStar.BLOCKED
			y < 0 -> SoulAStar.SOLID
			x == 2 && y in 0..3 -> if (z == 0 && y <= 1) gapCell else SoulAStar.SOLID
			else -> SoulAStar.AIR
		}
	}

	@Test fun pathThroughGapWithVerticalChain() {
		val chain = SoulAStar.classifyShape(px(6), px(10), 0.0, 1.0, px(6), px(10))
		val p = SoulAStar.find(G(chain), P(0, 0, 0), P(4, 0, 0))
		assertNotNull(p)
	}

	@Test fun fullBlockInGapStillFails() {
		assertNull(SoulAStar.find(G(SoulAStar.SOLID), P(0, 0, 0), P(4, 0, 0), nodeCap = 5_000))
	}

	private class M(val m: Map<P, Byte>) : SoulAStar.Grid {
		override fun cell(x: Int, y: Int, z: Int): Byte = when {
			x !in -20..20 || z !in -20..20 || y !in -20..30 -> SoulAStar.BLOCKED
			else -> m[P(x, y, z)] ?: if (y < 0) SoulAStar.SOLID else SoulAStar.AIR
		}
	}

	@Test fun raisedSoulInAlcoveNextToChainsIsReached() {

		val chain = SoulAStar.classifyShape(px(6), px(10), 0.0, 1.0, px(6), px(10))
		val m = HashMap<P, Byte>()
		for (z in -20..20) for (y in 0..5) m[P(4, y, z)] = SoulAStar.SOLID
		val soul = P(4, 3, 0)
		m.remove(soul)
		for (y in 1..4) m[P(3, y, 0)] = chain
		val g = M(m)

		assertNull(SoulAStar.find(g, P(0, 0, 0), soul, nodeCap = 5_000))
		val path = SoulAStar.find(g, P(0, 0, 0), soul, nodeCap = 5_000, goalRadius = 3.0, goalUp = 4, sight = true)
		assertNotNull(path)
	}

	@Test fun diagonalStepUpCorner() {

		val m = HashMap<P, Byte>()
		for ((x, z) in listOf(1 to 1, 1 to 0, 0 to 1)) m[P(x, 0, z)] = SoulAStar.SOLID
		val out = ArrayList<P>()
		SoulAStar.neighbours(M(m), P(0, 0, 0), out)
		assert(P(1, 1, 1) in out) { "diagonal step-up missing: $out" }

		m[P(1, 1, 0)] = SoulAStar.SOLID
		val out2 = ArrayList<P>()
		SoulAStar.neighbours(M(m), P(0, 0, 0), out2)
		assert(P(1, 1, 1) !in out2)
	}

	@Test fun sealedAlcoveStillFails() {
		val m = HashMap<P, Byte>()
		for (z in -20..20) for (y in 0..5) m[P(4, y, z)] = SoulAStar.SOLID
		for (z in -20..20) for (y in 0..5) m[P(2, y, z)] = SoulAStar.SOLID
		val soul = P(4, 3, 0); m.remove(soul)
		assertNull(SoulAStar.find(M(m), P(0, 0, 0), soul, nodeCap = 5_000, goalRadius = 3.0, goalUp = 4, sight = true))
	}
}
