// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenMiscA
import dev.ashtonite365.apex.features.garden.GardenMiscB
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch35GoldenTest {
	@Test
	fun garden() {
		val S = Batch35Garden
		val out = mutableListOf<Pair<String, String>>()
		for (t in S.times) out += "season|$t" to Golden.run { "${GardenMiscB.seasonIndex(t)}" }
		out += "perks" to "${GardenMiscB.seasonPerks}"
		out += "vinyls" to "${GardenMiscB.vinylPests}"
		for (p in S.playing) out += "playing|$p" to "${GardenMiscB.parsePlaying(p)}"
		for (l in S.logbook) out += "rate|$l" to Golden.run { "${GardenMiscB.acceptRate(l)}" }
		for (s in S.screens) for (n in S.names) for ((li, l) in S.lores.withIndex()) out += "slot|$s|$n|$li" to Golden.run { "${GardenMiscA.slotNumber(s, n, l)}" }
		for (s in S.slots) for ((li, l) in S.lores.withIndex()) out += "comp|$s|$li" to Golden.run { "${GardenMiscA.composterNumber(s, l)}" }
		for (g in S.gaps) out += "gap|$g" to "${GardenMiscA.addGap(g)}"
		for ((m, p) in S.crops) out += "cropsin|$m|$p" to "${GardenMiscA.cropsIn(m, p)}"
		val n = Golden.verify("batch35-garden", out, minCases = 975)
		assertTrue(n >= 975)
	}
}
