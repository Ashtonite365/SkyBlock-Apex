// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.profile

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.profile.PlayerProfileMenu
import dev.ashtonite365.apex.features.menus.profile.PlayerProfileParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class PlayerProfileMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()
	private val d = RealDumpFixtures.load("player-profile-1019")

	@Test fun routing() {
		MenuRegistry.register(PlayerProfileMenu)
		assertSame(PlayerProfileMenu, MenuRegistry.match("Tamimplayz's Profile"))

		assertNull(MenuRegistry.match("Papierwueste300"))
		assertNull(MenuRegistry.match("Catacombs Profile"))
	}

	@Test fun parsesTheRealPage() {
		val m = PlayerProfileParser.parse(d.title, d.slots)!!
		assertEquals("[MVP+] Tamimplayz", m.heading)
		assertTrue(m.chips.any { it.first == "SkyBlock Level" && it.second == "121" }, "${m.chips}")
		val gear = m.sections.first { it.title == "Equipment" }.tiles
		assertTrue(gear.any { it.name == "Wise Glacite Helmet" && it.slot == 11 })
		assertTrue(gear.any { it.name == "[Lvl 100] Mithril Golem" && it.slot == 47 })

		assertTrue(gear.none { it.name.matches(Regex("^Empty .* Slot$")) })
		assertEquals("Empty: Gloves / Bracelet", gear.first { it.slot == 37 }.name)
		assertTrue(gear.none { it.clickable })
		val actions = m.sections.first { it.title == "Actions" }.tiles
		assertEquals(listOf(15, 16, 24, 25, 31, 33, 34), actions.map { it.slot })
		assertTrue(actions.all { it.clickable && !it.delayed && it.button == 0 })
		assertEquals(listOf("Close" to 49), m.nav)
	}

	@Test fun unreadablePageIsNull() {
		assertNull(PlayerProfileParser.parse("Tamimplayz's Profile", emptyList()))
		assertNull(PlayerProfileParser.parse("Something", d.slots))
	}

	@Test fun hypixelPositions() {
		val d2 = RealDumpFixtures.load("player-profile-1021")
		val m = PlayerProfileParser.parse(d2.title, d2.slots)!!
		val bySlot = m.allTiles.associate { it.slot to it.name }
		assertEquals("Visit Island", bySlot[15]); assertEquals("Trade Request", bySlot[16])
		assertEquals("Invite to Island", bySlot[24]); assertEquals("Co-op Request", bySlot[25])
		assertEquals("Personal Vault", bySlot[33]); assertEquals("[VIP] Clandice's Museum", bySlot[34])
		assertEquals("Dimensional Yog Helmet", bySlot[11]); assertEquals("Royal Titanium Necklace", bySlot[10])
		assertEquals("[Lvl 66] Megalodon", bySlot[47])
		assertEquals(22, PlayerProfileParser.head(d2.title, d2.slots)!!.slot)
		listOf("Health", "Strength", "Defense", "Speed", "Crit Chance", "Crit Damage", "Intelligence", "SkyBlock Level")
			.forEach { k -> assertTrue(m.chips.any { it.first.trim().endsWith(k) }, "$k in ${m.chips}") }

		val (cw, ch) = dev.ashtonite365.apex.features.menus.profile.PlayerProfileLayout.cellSize(448, 180, 6)
		fun c(slot: Int) = dev.ashtonite365.apex.features.menus.profile.PlayerProfileLayout.cell(slot, 0, 0, cw, ch)
		assertEquals(c(11).x, c(38).x); assertEquals(c(15).y, c(16).y); assertTrue(c(16).x > c(15).x)
		assertTrue(c(24).y > c(15).y); assertEquals(c(2).x, c(11).x); assertEquals(c(10).x + cw + 2, c(11).x)

		assertEquals(0..5, dev.ashtonite365.apex.features.menus.profile.PlayerProfileLayout.rowSpan(m.allTiles.map { it.slot } + 22))
		assertTrue(cw * 9 + 16 <= 448)
	}

	@Test fun emptyFixedSlotsKeepTheirCells() {
		val d2 = RealDumpFixtures.load("player-profile-1021")

		val bare = d2.slots.map { if (it.slot in PlayerProfileParser.FIXED) it.copy(name = "Empty ${PlayerProfileParser.FIXED.getValue(it.slot).first} Slot", lore = emptyList()) else it }
		val full = PlayerProfileParser.parse(d2.title, d2.slots)!!
		val m = PlayerProfileParser.parse(d2.title, bare)!!
		val gear = m.sections.first { it.title == "Equipment" }.tiles
		assertEquals(PlayerProfileParser.FIXED.keys.sorted(), gear.map { it.slot })
		assertTrue(gear.all { PlayerProfileParser.isEmptyTile(it) && !it.clickable })
		assertEquals("Empty: Helmet", gear.first { it.slot == 11 }.name)
		assertEquals("Empty: Boots", gear.first { it.slot == 38 }.name)
		listOf(11, 20, 29, 38, 10, 19, 28, 37, 47).forEach { assertNotNull(PlayerProfileParser.silhouette(it)) }

		val blank = d2.slots.filter { it.slot != 20 }
		assertEquals("Empty: Chestplate", PlayerProfileParser.parse(d2.title, blank)!!.allTiles.first { it.slot == 20 }.name)

		val l = dev.ashtonite365.apex.features.menus.profile.PlayerProfileLayout
		assertEquals(l.rowSpan(full.allTiles.map { it.slot } + 22), l.rowSpan(m.allTiles.map { it.slot } + 22))
		assertEquals(full.allTiles.map { it.slot }.sorted(), m.allTiles.map { it.slot }.sorted())

		assertEquals("Empty: Held Item", full.allTiles.first { it.slot == 2 }.name)

		PlayerProfileParser.FIXED.values.forEach { (_, k) ->
			assertNotNull(javaClass.getResource("/assets/apex/textures/gui/sprites/pv_empty_$k.png"), k)
		}
	}
}
