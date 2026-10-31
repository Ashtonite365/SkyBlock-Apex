// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.apexmarket.CookieConverter
import dev.ashtonite365.apex.features.dungeon.CroesusHelpers
import dev.ashtonite365.apex.features.plus.fishing.PlusThunderSparks
import dev.ashtonite365.apex.features.plus.foraging.PlusForagingChat
import dev.ashtonite365.apex.features.plus.rift.PlusRiftAreas
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch17GoldenTest {
	private fun kismetLines(): List<Pair<String, Component>> {
		val strike = Style.EMPTY.withStrikethrough(true)
		fun lit(s: String, st: Style = Style.EMPTY) = Component.literal(s).withStyle(st)
		return listOf(
			"plain" to lit("Kismet Feather"),
			"struck" to lit("Kismet Feather", strike),
			"struckPart" to lit("Used: ").append(lit("1x Kismet Feather", strike)),
			"struckOther" to lit("Kismet Feather ").append(lit("Other", strike)),
			"split" to lit("Kismet ", strike).append(lit("Feather", strike)),
			"parentStrike" to lit("x", strike).append(lit("Kismet Feather")),
			"parentStrikeOff" to lit("x", strike).append(lit("Kismet Feather", Style.EMPTY.withStrikethrough(false))),
			"fmt" to lit("Kismet Feather").withStyle(ChatFormatting.STRIKETHROUGH, ChatFormatting.GRAY),
			"lower" to lit("kismet feather", strike),
			"empty" to lit(""),
			"nested" to lit("").append(lit("a").append(lit("Kismet Feather!!", strike))),
		)
	}

	@Test
	fun sh() {
		val S = Batch17Sh
		val out = mutableListOf<Pair<String, String>>()
		for (h in S.sweepHeaders) out += "sweepH|$h" to "${PlusForagingChat.parseSweepHeader(h)}"
		for (t in S.toughness) out += "tough|$t" to "${PlusForagingChat.parseToughness(t)}"
		for ((a, b, c) in S.sweeps) out += "sum|$a|$b|$c" to PlusForagingChat.summary(PlusForagingChat.Sweep(a, b, c))
		for ((i, l) in S.cookieLines.withIndex()) {
			out += "amt|$i|$l" to "${CookieConverter.parseCookieAmount(l)}"
			out += "bits|$i|$l" to "${CookieConverter.parseBitsCost(l)}"
		}
		for (t in S.tenths) out += "bpc|$t" to "${CookieConverter.bitsPerCookie(t)}"
		for ((c, b) in S.cpb) out += "cpb|$c|$b" to "${CookieConverter.coinsPerBit(c, b)}"
		out += "const" to "${CookieConverter.BASE_BITS_PER_COOKIE} ${CookieConverter.COOKIE}"
		for ((i, l) in S.croesusLores.withIndex()) out += "run|$i|$l" to "${CroesusHelpers.runState(l)}"
		for ((n, c) in kismetLines()) out += "kismet|$n" to Golden.run { "${CroesusHelpers.isUsedKismetLine(c)}" }
		for ((i, t) in S.textures.withIndex()) out += "tex|$i|$t" to Golden.run { "${PlusThunderSparks.textureHash(t)}" }
		for ((i, s) in S.skulls.withIndex()) out += "skull|$i" to Golden.run { "${PlusThunderSparks.sparkHashFrom(s)}" }
		for (v in S.vermin) out += "vermin|$v" to "${PlusRiftAreas.verminType(v)}"
		val n = Golden.verify("batch17-sh", out, minCases = 850)
		assertTrue(n >= 850)
	}
}
