// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.garden.GardenTools
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.stripSectionCodes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch11GoldenTest {
	private fun tooltip(raw: List<String>, vp: Boolean, cv: Boolean): String {
		val lines = raw.toMutableList()
		for ((i, s) in GardenTools.annotate(raw.map { it.stripSectionCodes() }, vp, cv, Batch11Garden.priceByName, Batch11Garden.trade)) lines[i] = lines[i] + s
		return lines.joinToString("\n")
	}

	@Test
	fun garden() {
		val G = Batch11Garden
		var annotated = 0
		var rates = 0
		val out = Golden.load("batch11-garden").map { (k, v) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"tip" -> tooltip(G.tooltips[p[1].toInt()], p[2].toBoolean(), p[3].toBoolean()).also { if (it.contains("§7(§6")) annotated++ }
					"one" -> tooltip(listOf("Items Required:", G.singleLines[p[1].toInt()]), true, true)
					"pestline" -> "${GardenTools.pestPlot(G.singleLines[p[1].toInt()])}"
					"trade" -> G.tables[p[1].toInt()].let { t -> GardenTools.bestTrade(t.json.entrySet(), { t.prices[it.skyblockId] }, p[2].toInt()).toString() }
					"win" -> {
						val w = GardenTools.CounterWindow(); var now = 1_700_000_000_000L
						G.sequences[p[1].toInt()].joinToString(";") { (dt, r) -> now += dt; val c = w.observe(now, r); "$c,${w.current},${w.perMinute()}" }
							.also { if (it.contains(Regex(",-?\\d+\\.\\d"))) rates++ }
					}
					"pest" -> "${GardenTools.pestPlot(G.pests[p[1].toInt()])}"
					"id" -> GardenTools.identifier
					else -> error(k)
				}
			}
		}
		Golden.verify("batch11-garden", out, minCases = 2000)
		assertTrue(annotated > 300, "annotated=$annotated")
		assertTrue(rates > 150, "rates=$rates")
	}
}
