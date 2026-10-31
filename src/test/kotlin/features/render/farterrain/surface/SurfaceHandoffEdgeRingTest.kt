// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.farterrain.surface

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoff
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceHandoffTimer
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurfaceHandoffEdgeRingTest {

	private fun gaps(neighboursEveryFrame: Boolean): Pair<Int, Int> {
		val serverDistance = 6
		val rd = 12
		val timer = SurfaceHandoffTimer()
		val ready = HashMap<Long, Long>()
		var gaps = 0
		var ringGaps = 0
		val path = { ms: Long ->
			val t = ms / 1000.0
			val x = if (t < 10) t * 20 else if (t < 20) 200 - (t - 10) * 20 else 0.0
			x to (t * 3)
		}
		var ms = 0L
		while (ms <= 25_000) {
			val (px, pz) = path(ms)
			val ccx = Math.floor(px).toInt() shr 4
			val ccz = Math.floor(pz).toInt() shr 4
			val loaded = { x: Int, z: Int -> Math.abs(x - ccx) <= serverDistance && Math.abs(z - ccz) <= serverDistance }
			for (x in ccx - serverDistance - 3..ccx + serverDistance + 3) for (z in ccz - serverDistance - 3..ccz + serverDistance + 3) {
				val k = SurfaceIndex.key(x, z)
				val withNeighbours = SurfaceHandoff.fallbackOwned(ccx, ccz, rd, x, z, loaded)
				if (withNeighbours) ready.putIfAbsent(k, ms) else ready.remove(k)
				val gameRenders = withNeighbours && ms - ready[k]!! >= 48
				val present = if (neighboursEveryFrame) withNeighbours else loaded(x, z)
				val built = present && withNeighbours && gameRenders
				val shown = if (present) timer.update(k, ms, built, 1, true) else { timer.release(k); false }
				if (shown && !gameRenders) {
					gaps++
					val ring = maxOf(Math.abs(x - ccx), Math.abs(z - ccz))
					if (ring == serverDistance) ringGaps++
				}
			}
			ms += 16
		}
		return gaps to ringGaps
	}

	@Test
	fun oldRuleLeavesTheOuterLoadedRingToNobody() {
		val (gaps, ring) = gaps(neighboursEveryFrame = false)
		assertTrue(gaps > 1000, "the old rule should reproduce the ring ($gaps)")

		assertTrue(ring * 100 >= gaps * 99, "$ring of $gaps on the outer ring")
	}

	@Test
	fun neighboursEveryFrameLeavesNoGap() {
		assertEquals(0 to 0, gaps(neighboursEveryFrame = true))
	}
}
