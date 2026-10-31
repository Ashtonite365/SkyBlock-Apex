// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.misc.DamageCommas
import dev.ashtonite365.apex.features.streamer.StreamerMode
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.math.Calculator
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch4GoldenTest {

	private fun legacyShort(n: Long): String {
		val s = DamageCommas.shortNumber(n)
		val letter = s.last().takeIf { it in "kMBT" } ?: return s
		val size = mapOf('k' to 1_000L, 'M' to 1_000_000L, 'B' to 1_000_000_000L, 'T' to 1_000_000_000_000L).getValue(letter)

		return if (n.toDouble() / size >= 100) s.dropLast(1).trimEnd('0') + letter else s
	}

	private fun BigDecimal.shown() = "${Calculator.format(this)} ${toPlainString()}"

	@Test
	fun repoMisc() {
		val vars = Calculator.VariableProvider { if (it == "ENCHANTED_DIAMOND") BigDecimal(160) else if (it == "INK_SACK;3") BigDecimal("2.5") else null }
		var zeroFixes = 0
		val out = Golden.load("batch4-repo-misc").map { (k, recorded) ->
			val kind = k.substringBefore('|')
			val arg = k.substringAfter('|')
			k to Golden.run {
				when (kind) {
					"calc" -> Calculator.evaluateOrNull(arg)?.shown() ?: "null"
					"calcv" -> Calculator.evaluateOrNull(arg, vars)?.shown() ?: "null"
					"plain" -> Calculator.isPlainNumber(arg).toString()
					"dmg" -> {
						val style = DamageCommas.Style.valueOf(arg.substringBefore('|'))
						val name = arg.substringAfter('|')
						val now = DamageCommas.rewrite(name, style) ?: "null"
						val old = DamageCommas.rewrite(name, style, ::legacyShort) ?: "null"
						if (now != old && old == recorded) zeroFixes++
						old
					}
					"short" -> legacyShort(arg.toLong()).also { if (it != DamageCommas.shortNumber(arg.toLong()) && it == recorded) zeroFixes++ }
					"commas" -> DamageCommas.format(arg.toLong(), DamageCommas.Style.COMMAS)
					"lobby" -> StreamerMode.filterLobbyNames(arg)
					"segs" -> StreamerMode.filterSegments(arg.split('¦')).joinToString("¦")
					else -> error(k)
				}
			}
		}
		Golden.verify("batch4-repo-misc", out, minCases = 1500)
		assertTrue(zeroFixes > 0, "the zero-trimming difference should show up in the corpus")
		assertEquals("100M", DamageCommas.shortNumber(100_000_000))
		assertEquals("1.5T", DamageCommas.shortNumber(1_500_000_000_000))
	}
}
