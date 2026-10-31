// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.skyblock.CommunityCompact
import dev.ashtonite365.apex.features.menus.skyblock.CommunityMenu
import dev.ashtonite365.apex.features.menus.skyblock.CommunityParser
import dev.ashtonite365.apex.features.menus.skyblock.CommunityTile
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CommunityCompactTest {
	private val d = RealDumpFixtures.load("community-upgrades-2042")
	private val u = CommunityParser.parse(d.title, d.slots)!!.upgrades!!
	private val measure: (String) -> Int = { MenuText.clean(it).length * 6 }
	private fun overlap(ax: Int, ay: Int, aw: Int, ah: Int, bx: Int, by: Int, bw: Int, bh: Int) = ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah

	@Test fun upgradesFitTheDefaultPanelWithEveryEntry() {
		for (width in listOf(280, 360, 468, 600)) {
			val lay = CommunityCompact.upgrades(u, width, measure)
			assertEquals(u.cells.map { it.slot }.sorted(), lay.cards.map { it.cell.slot }.sorted(), "every entry shown at $width")
			for (c in lay.cards) {
				assertTrue(c.x >= 0 && c.x + c.w <= width, "inside at $width: $c")
				val tw = if (c.section) c.w - 2 * CommunityCompact.PAD else c.w - c.textX - CommunityCompact.PAD
				assertTrue(measure(c.name) <= tw, "name fits: ${c.name}")
				for (l in c.lines) assertTrue(measure(l) <= tw, "line fits: $l")
				if (!c.section) assertTrue(CommunityCompact.PAD + 1 + (1 + c.lines.size) * CommunityCompact.LINE <= c.h, "text inside the card height")
				assertTrue(c.lines.size <= CommunityCompact.MAX_LINES)
				for (o in lay.cards) if (o !== c) assertFalse(overlap(c.x, c.y, c.w, c.h, o.x, o.y, o.w, o.h), "overlap $c / $o")
			}
			assertTrue(lay.cards.filter { !it.section }.all { it.w >= CommunityCompact.UPGRADE_MIN_W || lay.cols == 1 }, "wide cards")
		}

		val (dw, dh) = CommunityMenu.defaultSize
		val grid = CommunityCompact.upgrades(u, dw - 12 - 6, measure).height
		val old = dev.ashtonite365.apex.features.menus.skyblock.UpgradesLayout.place(u, dw - 12 - 6, measure).height
		assertTrue(grid * 2 < old, "compact $grid vs old $old")
		assertTrue(dh < 1170 && grid < 2 * (dh - 99 - 38), "about one scroll in the smaller panel: $grid")
	}

	@Test fun condensedLinesKeepTheKeyFacts() {
		val lore = listOf("Profile Upgrade", "", "Expand your island by adding", "buildable space on each side.", "", "Your island size: 169x169",
			"Next tier size: 177x177 blocks", "", "Cost", "175 Gems", "Duration: 1d", "", "Cannot afford to start!")
		val k = CommunityCompact.keyLines(lore)
		assertEquals(3, k.size)
		assertTrue(k.contains("Cost: 175 Gems"), "$k"); assertEquals("Cannot afford to start!", k.last())
		assertTrue(k.first().startsWith("Your island size") || k.first().startsWith("Next tier"), "$k")
		assertEquals(listOf("Maxed out!"), CommunityCompact.keyLines(listOf("Account Upgrade", "Earn some coins", "", "Maxed out!")))
	}

	@Test fun bitsShopIsDenseAndFits() {
		val names = listOf("God Potion", "Kismet Feather", "Kat Items", "Upgrade Components", "Matriarch's Perfume", "Hologram",
			"Ditto Blob", "Builder's Wand", "Block Zapper", "Bits Talisman", "Bitbug", "Sacks", "Portalizer", "Abiphone Supershop",
			"Inferno Fuel", "Heat Core", "Hyper Catalyst", "Ultimate Carrot Candy", "Colossal Experience Bottle", "Jumbo Backpack",
			"Minion Storage X-pender", "Pocket Sack-in-a-Sack", "Dungeon Sack", "Rune Sack", "Flower Sack", "Dwarven Sack", "Crystal Hollows Sack",
			"Expertise", "Compact", "Cultivating")
		val tiles = names.mapIndexed { i, n ->
			val lore = if (i % 4 == 2) listOf("Click to browse!") else listOf("Cost", "${(i + 1) * 300} Bits", "", "Click to trade!")
			CommunityTile(10 + i, n, null, lore, lore = lore)
		}
		for (width in listOf(280, 350, 468)) {
			val (cards, total) = CommunityCompact.bits(tiles, width, measure)
			val cols = cards.map { it.x }.distinct().size
			assertTrue(cols >= 3 || width < 3 * CommunityCompact.BITS_MIN_W + 6, "3+ columns at $width")
			for (c in cards) {
				assertTrue(c.x + c.w <= width && measure(c.name) <= c.w - CommunityCompact.PAD * 2 - CommunityCompact.ICON - 3 && measure(c.cost) <= c.w - CommunityCompact.PAD * 2 - CommunityCompact.ICON - 3, "$c")
				for (o in cards) if (o !== c) assertFalse(overlap(c.x, c.y, c.w, c.h, o.x, o.y, o.w, o.h))
			}
			if (width == 468) assertTrue(cols >= 4 && total <= 2 * (340 - 99 - 24), "dense: $cols cols, $total tall")
		}
		assertEquals("§b300 Bits", CommunityCompact.bitsCost(tiles[0])); assertEquals("§eBrowse", CommunityCompact.bitsCost(tiles[2]))
	}

	@Test fun vanillaButtonClearOfTheStyleTag() {
		for (s in listOf(StyleState(style = Style.SKYGLASS), StyleState(style = Style.FORGE))) for (y in listOf(2, 10, 40)) {
			val b = MenuBounds(20, y, 480, 300); val vw = 46
			val vx = CommunityMenu.vanillaX(b.x, b.y, b.w, vw, s)
			assertTrue(vx >= b.x && vx + vw <= b.x + b.w)
			val inside = y < StyleBar.H + 4
			if (inside) assertTrue(vx + vw <= StyleBar.geometry(b.x + b.w - 4, b.y + 4, s).dropX - 4, "left of the style tag at y $y")
		}
	}
}
