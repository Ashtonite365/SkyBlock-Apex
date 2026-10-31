// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import net.minecraft.world.phys.Vec3
import kotlin.random.Random

object Batch28Sh {
	val positions: List<Vec3> by lazy {
		val r = Random(2801)
		listOf(Vec3.ZERO, Vec3(Double.NaN, 0.0, 0.0), Vec3(111.5, 113.5, 73.5), Vec3(-0.0, 0.0, -0.0), Vec3(1e300, 1e300, 1e300)) +
			List(80) { Vec3(r.nextDouble(-20.0, 130.0), r.nextDouble(100.0, 140.0), r.nextDouble(10.0, 150.0)) }
	}
	val doneMasks: List<Long> by lazy { val r = Random(2802); listOf(0L, -1L) + List(10) { r.nextLong() } }
	val computeArgs = listOf(Triple(0L, 0, 0), Triple(100L, 0, 0), Triple(100L, -1, 1), Triple(300L, 100, 2), Triple(-50L, 3, 3), Triple(Long.MAX_VALUE, 1, 4), Triple(7L, 3, 5), Triple(1L, Int.MAX_VALUE, 6))
	val milestoneSeq: List<Map<String, Long>> by lazy {
		val r = Random(2803)
		val crops = listOf("WHEAT", "CARROT", "NETHER_WART", "CACTUS")
		List(30) { if (r.nextInt(6) == 0) emptyMap() else crops.associateWith { r.nextLong(0, 1_000_000) }.filter { r.nextInt(5) > 0 } }
	}
}
