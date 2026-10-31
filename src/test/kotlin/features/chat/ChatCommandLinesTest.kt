// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.chat.PartyCommands
import dev.ashtonite365.apex.features.chat.PartyCommands.Channel
import dev.ashtonite365.apex.features.chat.PartyCommands.Incoming
import dev.ashtonite365.apex.util.stripSectionCodes

class ChatCommandLinesTest {
	private fun parse(line: String) = PartyCommands.parse(line.stripSectionCodes())

	@Test
	fun partyLinesFromTheLog() {

		assertEquals(Incoming(Channel.PARTY, "supvik", "!f3"), parse("§9Party §8> §b[MVP§2+§b] supvik§f: !f3"))
		assertEquals(Incoming(Channel.PARTY, "supvik", "!dt fortnite"), parse("§9Party §8> §b[MVP§2+§b] supvik§f: !dt fortnite"))
		assertEquals(Incoming(Channel.PARTY, "00Raptor10", "!coords"), parse("§9Party §8> §700Raptor10§f: !coords"))
		for ((rank, name) in listOf("" to "Enoxium", "[VIP] " to "Dubr1s", "[VIP+] " to "zxgf", "[MVP+] " to "Azvooy", "[MVP++] " to "Foo", "[YOUTUBE] " to "Yt"))
			assertEquals(Incoming(Channel.PARTY, name, "!fps"), parse("Party > $rank$name: !fps"))

		assertEquals(Incoming(Channel.PARTY, "Damiansumo", "!cf"), parse("Party > [475] ❁ [MVP+] Damiansumo: !cf"))
		assertEquals(Incoming(Channel.PARTY, "Foo", "!cf"), parse("Party > [VIP] Foo ♲: !cf"))
	}

	@Test
	fun guildAndDmLines() {
		assertEquals(Incoming(Channel.GUILD, "Foo", "!coords"), parse("§2Guild > §b[MVP§c+§b] Foo §e[Officer]§f: !coords"))
		assertEquals(Incoming(Channel.GUILD, "Bar", "!8ball"), parse("Guild > Bar [Member]: !8ball"))
		assertEquals(Incoming(Channel.GUILD, "Baz", "!dice"), parse("Guild > [302] ⚝ [VIP] Baz ⚒ [Member]: !dice"))
		assertEquals(Incoming(Channel.PRIVATE, "Foo", "!inv"), parse("§dFrom §r§b[MVP§0++§b] Foo§r§7: §r§7!inv"))
		assertEquals(Incoming(Channel.PRIVATE, "Bar", "!coords"), parse("From Bar: !coords"))
		assertEquals(Incoming(Channel.COOP, "Qux", "!fps"), parse("Co-op > [VIP+] Qux: !fps"))

		assertNull(parse("To [MVP+] Foo: !inv"))
		assertNull(parse("[122] ⚝ Ashtonite365: !coords"))
		assertNull(parse("Party Finder > Foo joined the dungeon group! (Mage Level 32)"))
	}

	@Test
	fun noAutoReplies() {
		val names = PartyCommands::class.java.declaredMethods.map { it.name }.toSet()
		for (gone in listOf("decide", "handle", "endOfRun", "run", "schedule")) assertFalse(gone in names, "$gone must be removed")
		assertEquals(setOf("chat-emotes"), PartyCommands.TConfig.allOptions.keys)
		assertEquals("hi ❤", PartyCommands.replaceEmotes("hi <3"))
		assertNull(PartyCommands.replaceEmotes("/warp hub <3"))
	}
}
