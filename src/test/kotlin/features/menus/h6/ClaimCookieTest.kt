// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.BoosterCookieParser
import dev.ashtonite365.apex.features.menus.h6.ClaimCookieParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ClaimCookieTest {
	private val d = RealDumpFixtures.load("claim-booster-cookie")

	@Test fun boosterCookieNoLongerClaimsIt() {
		assertFalse(BoosterCookieParser.TITLE.containsMatchIn(d.title))
		assertTrue(BoosterCookieParser.TITLE.containsMatchIn("Booster Cookie"))
		assertTrue(ClaimCookieParser.TITLE.containsMatchIn(d.title))
	}

	@Test fun readsTheClaimPage() {
		val m = ClaimCookieParser.parse(d.title, d.slots)!!
		assertEquals(m, ClaimCookieParser.parse(d.title, d.slots.filter { it.cleanName.isNotEmpty() }), "snapshot vs full")
		val t = m.allTiles.single()
		assertEquals(13, t.slot); assertEquals(0, t.button); assertTrue(t.clickable)
		assertEquals("Safety Squad Booster Cookie", t.name)
		assertEquals(listOf("Buff" to "4 days"), m.chips)
		assertTrue(t.tooltip.none { it.startsWith("Click to claim") || it == "LEGENDARY" }, "${t.tooltip}")
		assertTrue(m.nav.isEmpty())
	}

	@Test fun structuralOnly() {
		assertNull(ClaimCookieParser.parse(d.title, d.slots.map { it.copy(lore = it.lore.filterNot { l -> l.startsWith("Click to claim") }) }))
		assertNull(ClaimCookieParser.parse("Claim Safety Badge", d.slots))
	}
}
