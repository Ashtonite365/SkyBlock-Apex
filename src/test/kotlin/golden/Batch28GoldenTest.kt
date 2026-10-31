// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenCropSpeedMeter
import dev.ashtonite365.apex.features.garden.GardenProgress
import dev.ashtonite365.apex.features.plus.dungeon.PlusTerminalInfo
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch28GoldenTest {
	@Test
	fun sh() {
		val S = Batch28Sh
		val out = mutableListOf<Pair<String, String>>()
		out += "spots" to "${PlusTerminalInfo.spots}"
		val all = PlusTerminalInfo.spots
		for ((mi, mask) in S.doneMasks.withIndex()) {
			val done = all.filterIndexed { i, _ -> (mask ushr i) and 1L == 1L }.toSet()
			for (section in 0..5) for ((pi, p) in S.positions.withIndex()) out += "closest|$mi|$section|$pi" to "${PlusTerminalInfo.closest(section, p, done)}"
		}
		val crops = GardenProgress.Crop.entries
		for ((c, b, ci) in S.computeArgs) out += "compute|$c|$b|$ci" to "${GardenCropSpeedMeter.compute(c, b, crops[ci % crops.size])}"
		val m = GardenCropSpeedMeter
		out += "lines0" to "${m.lines()}"
		m.onMilestonesRead(mapOf("WHEAT" to 5L))
		out += "linesDisabled" to "${m.lines()}"
		m.enabled = true
		for ((i, t) in S.milestoneSeq.withIndex()) {
			m.onMilestonesRead(t)
			out += "ms|$i|$t" to "${m.lines()}"
		}
		m.enabled = false
		val n = Golden.verify("batch28-sh", out, minCases = 6150)
		assertTrue(n >= 6150)
	}
}
