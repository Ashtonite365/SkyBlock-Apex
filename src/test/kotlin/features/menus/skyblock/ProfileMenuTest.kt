// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.skyblock.ProfileParser
import dev.ashtonite365.apex.features.menus.skyblock.ProfileSlotState
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ProfileMenuTest {
	private val d = RealDumpFixtures.load("profile-management")

	@Test
	fun realDump() {
		val m = ProfileParser.parse(d.title, d.slots)!!
		assertEquals(listOf(11, 12, 13, 14, 15), m.tiles.map { it.slot })
		assertEquals("Lemon", m.active?.name)
		assertEquals("Classic", m.active?.mode)
		assertEquals(listOf(ProfileSlotState.PROFILE, ProfileSlotState.EMPTY, ProfileSlotState.LOCKED, ProfileSlotState.LOCKED, ProfileSlotState.LOCKED), m.tiles.map { it.state })
		assertEquals(30, m.back); assertEquals(31, m.close)
		val summary = ProfileParser.loreSummary(m.tiles[0].lore)
		assertTrue("Bank Coins: 375,448,242" in summary)
		assertFalse(summary.any { it.startsWith("Click") || it == "Selected slot" })
	}

	@Test
	fun coopMembersAndModeSynthetic() {
		val t = ProfileParser.tile(SlotText(12, "Profile: Banana", listOf("Ironman", "", "Co-op Members:", "[MVP+] Ashton", "Mustangr2", "", "Click to switch!")))!!
		assertEquals("Ironman", t.mode)
		assertEquals(listOf("Ashton", "Mustangr2"), t.members)
		assertFalse(t.active)
	}

	@Test
	fun mismatchFallsBack() {
		assertNull(ProfileParser.parse("SkyBlock Menu", d.slots))
		assertNull(ProfileParser.parse(d.title, d.slots.filter { !it.name.startsWith("Profile:") }))
		assertNull(ProfileParser.parse(d.title, emptyList()))
	}
}
