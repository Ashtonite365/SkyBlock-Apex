// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.plus.fishing.PlusFishingSmall
import dev.ashtonite365.apex.features.plus.fishing.PlusTrophyHelpers
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch31GoldenTest {
	@Test
	fun fish() {
		val S = Batch31Fish
		val out = mutableListOf<Pair<String, String>>()
		for ((i, g) in S.geyser.withIndex()) out += "geyser|$i|$g" to "${PlusTrophyHelpers.isGeyserParticle(g[0] as Boolean, g[1] as Int, g[2] as Float, g[3] as Float, g[4] as Float, g[5] as Float)}"
		for (x in S.boxes) for (z in S.boxes) out += "box|$x|$z" to "${PlusTrophyHelpers.geyserBox(Vec3(x, 0.0, z))}"
		for (l in S.lores) out += "trophy|$l" to "${PlusTrophyHelpers.isTrophyLore(l)}"
		out += "odger" to "${PlusTrophyHelpers.ODGER}"
		for ((i, f) in S.flay.withIndex()) out += "flay|$i|$f" to "${PlusFishingSmall.isFlayParticle(f[0] as Boolean, f[1] as Double, f[2] as Double, f[3] as Double, f[4] as Int, f[5] as Float)}"
		for (n in S.names) {
			out += "bait|$n" to "${PlusFishingSmall.isBaitName(n)}"
			for (c in S.counts) for (b in listOf(true, false)) out += "label|$n|$c|$b" to PlusFishingSmall.itemLabel(n, c, b)
		}
		for (c in S.cocoon) out += "cocoon|$c" to "${PlusFishingSmall.cocoonedMob(c)}"
		for (l in S.lists) out += "mobs|$l" to "${PlusFishingSmall.parseMobList(l)}"
		val n = Golden.verify("batch31-fish", out, minCases = 840)
		assertTrue(n >= 840)
	}
}
