// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object HypixelChatCorpus {
	val fonts = listOf("vanilla" to vanillaAdvance, "wide" to wideAdvance, "narrow" to narrowAdvance)
	val widths = listOf(120, 200, 320, 410)

	private fun lit(s: String, vararg f: ChatFormatting): MutableComponent = Component.literal(s).withStyle(*f)
	private fun pad(text: String, bold: Boolean = false): String {

		val w = text.sumOf { vanillaAdvance(it.code) + if (bold && it != ' ') 1 else 0 }
		return " ".repeat(((320 - w) / 2 / 4).coerceAtLeast(0)) + text
	}

	fun messages(): List<Component> {
		val out = mutableListOf<Component>()
		for (c in listOf('-', '—', '▬', '━')) for (n in listOf(3, 4, 5, 6, 20, 53, 64, 80)) {
			out += lit(c.toString().repeat(n), ChatFormatting.BLUE, ChatFormatting.STRIKETHROUGH)
			out += lit(" " + c.toString().repeat(n) + "  ", ChatFormatting.GOLD)
		}
		out += Component.empty().append(lit("-----", ChatFormatting.AQUA)).append(lit("-----", ChatFormatting.RED))
		out += lit("--=--=--=--")
		for (c in listOf('-', '▬', '━')) for (title in listOf("Party", " Friends (Page 1 of 2) ", "SkyBlock Profile Viewer", "x")) for (n in listOf(4, 10, 18, 22, 25, 30)) {
			val bar = c.toString().repeat(n)
			out += Component.empty().append(lit(bar, ChatFormatting.BLUE, ChatFormatting.STRIKETHROUGH)).append(lit(" ")).append(lit(title, ChatFormatting.GOLD, ChatFormatting.BOLD)).append(lit(" ")).append(lit(bar, ChatFormatting.BLUE, ChatFormatting.STRIKETHROUGH))
			out += lit("$bar $title ${c.toString().repeat(n + 3)}", ChatFormatting.YELLOW)
			out += lit("$bar$title$bar")
		}
		val centred = listOf("SKYBLOCK EVENT", "Mayor Diana is now in office!", "You earned 1,234 coins", "A", "§lBOLD-ish", "The Catacombs - Floor VII Stats", "Team Score: 300 (S+)", "i i i i i i i i i i i", "@@@@@@@@@@@@@@@@")
		for (t in centred) {
			out += lit(pad(t), ChatFormatting.GREEN)
			out += lit(pad(t, bold = true), ChatFormatting.RED, ChatFormatting.BOLD)
			out += lit(" $t")
			out += lit("   $t   ")
			out += lit(pad(t) + "   ")
			out += lit(pad(t).drop(3))
			out += lit("      " + pad(t))
			out += lit(t)
			out += Component.empty().append(lit(pad(t).takeWhile { it == ' ' })).append(lit(t.take(t.length / 2), ChatFormatting.AQUA)).append(lit(t.drop(t.length / 2), ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
		}

		val bar = "▬".repeat(64)
		out += Component.empty().append(lit(bar, ChatFormatting.GREEN, ChatFormatting.BOLD)).append("\n").append(lit(pad("The Catacombs - Floor VII"), ChatFormatting.WHITE, ChatFormatting.BOLD)).append("\n\n").append(lit(pad("Team Score: 317 (S+)"), ChatFormatting.YELLOW)).append("\n").append(lit(bar, ChatFormatting.GREEN, ChatFormatting.BOLD))
		out += Component.empty().append(lit("-----------------------------------------------------", ChatFormatting.BLUE)).append("\n").append(lit("Party Members (3)", ChatFormatting.GOLD)).append("\n").append(lit("-----------------------------------------------------", ChatFormatting.BLUE))
		out += lit("plain line\nsecond line\n")
		out += lit("\n\n")
		out += lit("")
		out += lit("     ")
		out += lit("A very long ordinary message that should wrap around because it is wider than the narrow chat window at all, yes indeed")
		out += lit(pad("A very long centred message that is wider than one hundred and sixty pixels"))
		return out
	}
}
