// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.ours

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.test.util.ours.Golden.Companion.golden
import dev.ashtonite365.apex.util.allSiblings
import dev.ashtonite365.apex.util.aqua
import dev.ashtonite365.apex.util.blue
import dev.ashtonite365.apex.util.bold
import dev.ashtonite365.apex.util.colouredBy
import dev.ashtonite365.apex.util.runsOnClick
import dev.ashtonite365.apex.util.darkGreen
import dev.ashtonite365.apex.util.darkGrey
import dev.ashtonite365.apex.util.ownLiteralText
import dev.ashtonite365.apex.util.styleCodeLetters
import dev.ashtonite365.apex.util.getLegacyFormatString
import dev.ashtonite365.apex.util.gold
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.hover
import dev.ashtonite365.apex.util.iterator
import dev.ashtonite365.apex.util.lime
import dev.ashtonite365.apex.util.pink
import dev.ashtonite365.apex.util.prependChild
import dev.ashtonite365.apex.util.purple
import dev.ashtonite365.apex.util.rebuildComponent
import dev.ashtonite365.apex.util.red
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.titleCase
import dev.ashtonite365.apex.util.toChatFormatting
import dev.ashtonite365.apex.util.trResolved
import dev.ashtonite365.apex.util.mapComponentTree
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.white
import dev.ashtonite365.apex.util.withColor
import dev.ashtonite365.apex.util.yellow

@ExtendWith(AutoBootstrapExtension::class)
class TextUtilGoldenTest {
	@Test
	fun stripSectionCodes() = golden("textutil-removeColorCodes") {
		case("formattingChars") { styleCodeLetters.sorted() }
		val fixed = listOf("", "§", "§§", "a§", "§a", "§l§cX§r", "x§kY§lZ§", "§§§l", "plain", "§zq", "§Lb§MN§r", "§§k")
		val f = Fuzz(11)
		val inputs = fixed + List(600) { f.str(f.formatty, 14) }
		for (s in inputs) {
			case("rm $s") { s.stripSectionCodes() }
			case("keep $s") { s.stripSectionCodes(true) }
			case("sb $s") { StringBuilder(s).stripSectionCodes(keepStyleCodes = true) }
		}
	}

	@Test
	fun components() = golden("textutil-components") {
		val f = Fuzz(12)
		repeat(300) { i ->
			val c = f.component()
			case("$i str") { c.toString() }
			case("$i unformatted") { c.bareText }
			case("$i direct") { c.ownLiteralText }
			case("$i legacy") { c.getLegacyFormatString() }
			case("$i legacyTrim") { c.getLegacyFormatString(trimmed = true) }
			case("$i iter") { c.iterator().map { it.toString() }.toList() }
			case("$i all") { c.allSiblings().map { it.toString() } }
			case("$i reconFT") { (c as FormattedText).rebuildComponent().toString() }
			case("$i reconFCS") { c.visualOrderText.rebuildComponent().toString() }
			case("$i transformSame") {
				c.copy().mapComponentTree { it }.toString()
			}
			case("$i transformNew") {
				c.copy().mapComponentTree { Component.literal("<" + it.string.length + ">").withStyle(it.style.withBold(true)) }.toString()
			}
			case("$i transformMutate") {
				c.copy().mapComponentTree { x -> (x as? net.minecraft.network.chat.MutableComponent)?.withStyle(ChatFormatting.RED) ?: x }.toString()
			}
		}
		case("fcs-composite") {
			FormattedCharSequence.composite(
				FormattedCharSequence.forward("ab", Style.EMPTY.withColor(ChatFormatting.RED)),
				FormattedCharSequence.forward("cd", Style.EMPTY.withColor(ChatFormatting.RED)),
				FormattedCharSequence.forward("", Style.EMPTY),
				FormattedCharSequence.forward("ef", Style.EMPTY.withBold(true)),
				FormattedCharSequence.forward("\uD83D\uDE00", Style.EMPTY),
			).rebuildComponent().toString()
		}
		case("fcs-empty") { FormattedCharSequence.EMPTY.rebuildComponent().toString() }
		case("ft-of") { FormattedText.of("hi", Style.EMPTY.withItalic(true)).rebuildComponent().toString() }
		case("ft-composite") { FormattedText.composite(FormattedText.of("a"), FormattedText.of(""), FormattedText.of("b", Style.EMPTY.withColor(ChatFormatting.GOLD))).rebuildComponent().toString() }
	}

	@Test
	fun colours() = golden("textutil-colours") {
		for (cf in ChatFormatting.entries) {
			case("lut ${cf.name}") { TextColor.fromLegacyFormat(cf)?.toChatFormatting() }
			case("with ${cf.name}") { Component.literal("x").withStyle { it.withBold(true).withItalic(true).withUnderlined(true) }.withColor(cf).toString() }
		}
		val f = Fuzz(13)
		repeat(200) { val v = if (it < 50) it * 0x050505 else f.r.nextInt(0x1000000); case("rgb $v") { TextColor.fromRgb(v).toChatFormatting() } }
		val helpers = listOf<Pair<String, (net.minecraft.network.chat.MutableComponent) -> Any?>>(
			"blue" to { it.blue() }, "aqua" to { it.aqua() }, "lime" to { it.lime() }, "darkGreen" to { it.darkGreen() },
			"purple" to { it.purple() }, "pink" to { it.pink() }, "yellow" to { it.yellow() }, "gold" to { it.gold() },
			"grey" to { it.grey() }, "darkGrey" to { it.darkGrey() }, "red" to { it.red() }, "white" to { it.white() },
			"bold" to { it.bold() }, "hover" to { it.hover(Component.literal("tip")) },
			"boolT" to { it.colouredBy(true) }, "boolF" to { it.colouredBy(false) },
			"boolCustom" to { it.colouredBy(false, ChatFormatting.AQUA, ChatFormatting.BLACK) },
			"click" to { it.runsOnClick("/warp hub") }, "clickBad" to { it.runsOnClick("warp hub") },
			"prepend" to { it.prependChild(Component.literal("pre")) },
		)
		for ((name, fn) in helpers) {
			case(name) {
				val c = Component.literal("x").withStyle { it.withItalic(true) }.append("y")
				val out = fn(c)
				"${out === c} $out"
			}
		}
	}

	@Test
	fun strings() = golden("textutil-strings") {
		val fixed = listOf("", "HELLO_WORLD", "hello world", "  double  space ", "_lead", "ALL_CAPS_123", "éclair_ÜBER", "a", "x_y_z", "ǆungla")
		val f = Fuzz(14)
		for (s in fixed + List(150) { f.str("aB_ cD1é", 10) }) case("title $s") { titleCase(s) }
		case("trResolved") { trResolved("apex.k", "a", 1, Component.literal("c")).toString() }
		case("trResolvedNone") { trResolved("apex.k").toString() }
	}
}
