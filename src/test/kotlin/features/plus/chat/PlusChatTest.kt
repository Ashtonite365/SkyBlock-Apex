// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.chat.SkyBlockChatFilters
import dev.ashtonite365.apex.features.chat.AnnouncementSwallower
import dev.ashtonite365.apex.features.chat.ArachneQuieter
import dev.ashtonite365.apex.features.plus.chat.PlusChatCompact

class PlusChatTest {
	@Test
	fun shortenCoins() {
		assertEquals("[Auction] X bought Y for 2.7m coins", PlusChatCompact.shortenNumbers("[Auction] X bought Y for 2,650,000 coins"))
		assertEquals("ALLOWANCE! You earned 50k coins!", PlusChatCompact.shortenNumbers("ALLOWANCE! You earned 50,000 coins!"))
		assertEquals("withdrawn 10.5k coins", PlusChatCompact.shortenNumbers("withdrawn 10.5k coins"))
	}

	@Test
	fun compactPotion() {
		assertEquals("§a§lPotion Effect! §rSpeed V §aby §fFoo",
			PlusChatCompact.compactPotion("BUFF! You were splashed by Foo with Speed V! Press TAB or type /effects to view your active effects!"))
		assertEquals("§a§lPotion Effect! §rPoisoned Candy I", PlusChatCompact.compactPotion("BUFF! You have gained Poisoned Candy I!"))
		assertNull(PlusChatCompact.compactPotion("hello"))
	}

	@Test
	fun bestiary() {
		val b = PlusChatCompact.BestiaryCompactor()
		assertFalse(b.feed("hello").first)
		assertTrue(b.feed("BESTIARY").first)
		b.feed("Zombie 4➜5"); b.feed("Rewards"); b.feed("Zombie 9➡10")
		val (hide, summary) = b.feed("▬".repeat(40))
		assertTrue(hide)
		assertEquals("Zombie 4➜5", summary!!.first)
		assertEquals("9➡10", b.milestone)
	}

	@Test
	fun watchdogAndArachne() {
		val w = AnnouncementSwallower.watchdog()
		assertFalse(w.swallow("hi"))
		assertTrue(w.swallow("[WATCHDOG ANNOUNCEMENT]"))
		assertTrue(w.swallow("Watchdog has banned 1 players"))
		assertTrue(w.swallow(""))
		assertFalse(w.swallow("after"))
		val a = ArachneQuieter()
		assertTrue(a.quieten("Arachne's Brood used Venom Shot on you hitting you for 19.8 damage and infecting you with venom."))
		assertTrue(a.quieten("[BOSS] Arachne: The Era of Spiders begins now."))
		assertFalse(a.quieten("normal"))
	}

	@Test
	fun darkAuction() {
		assertNull(PlusChatCompact.darkAuction("[NPC] Sirius: Next up we have an Ender Artifact, the starting bid is 50,000 Coins!"))
		assertEquals("Ender Artifact", PlusChatCompact.darkAuction("[NPC] Sirius: The highest bidder was qtLuna with a bid of 25,950,000 Coins!"))
		assertNull(PlusChatCompact.darkAuction("[NPC] Sirius: No one made any bids!"))
	}
}
