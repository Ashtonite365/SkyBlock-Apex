// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.chat.ChatCentring
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class ChatCentringGoldenTest {
	@Test
	fun matchesRecordedLayout() {
		ChatCentring.TConfig.explicitDefaultLoad()
		val actual = mutableListOf<Pair<String, String>>()
		for ((fontName, adv) in HypixelChatCorpus.fonts) {
			val font = testFont(adv)
			for (width in HypixelChatCorpus.widths) for ((id, msg) in HypixelChatCorpus.messages().withIndex())
				actual += "$fontName|$width|$id" to Golden.run {
					ChatCentring.relayout(msg, font, width, ::testChatWrap)?.joinToString("\n") { encodeStyled(it) } ?: "UNCHANGED"
				}
		}
		val recorded = Golden.load("hypixel-chat-layout")
		org.junit.jupiter.api.Assertions.assertTrue(recorded.count { it.second.startsWith("THROWS") } == 0, "fixture must not be degenerate")
		org.junit.jupiter.api.Assertions.assertTrue(recorded.count { it.second != "UNCHANGED" } > 500, "fixture should exercise the re-layout")
		Golden.verify("hypixel-chat-layout", actual, minCases = 2000)
	}

	@Test
	fun stockWidths() {
		assertEquals(4, ChatCentring.stockWidth(' ', true))
		assertEquals(3, ChatCentring.stockWidth('i', true))
		assertEquals(9, ChatCentring.stockWidth('▬', false))
		assertEquals(6, ChatCentring.stockWidth('A', false))
		for (c in ' '..'~') assertEquals(vanillaAdvance(c.code), ChatCentring.stockWidth(c, false), "width of '$c'")
	}
}
