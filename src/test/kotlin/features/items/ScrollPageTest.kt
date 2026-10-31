// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.recipes.AcquisitionInfo
import dev.ashtonite365.apex.features.items.recipes.HowToGetModel
import dev.ashtonite365.apex.features.items.recipes.ScrollPage

class ScrollPageTest {

	private val stonefish = listOf(
		"§7Trophy Fish can be occasionally caught while fishing on the Crimson Isle, with different requirements to catch each Trophy Fish. There are four tiers of each Trophy Fish: Bronze, Silver, Gold, and Diamond.",
		"§7The Trophy Chance Stat can be used to increase the chance of Trophy Fish being fished up. The Midas Lure perk from Marigold and Radiant Fisher perk from Gemma only apply to Gold and Diamond Trophies respectively.",
		"§7When the player catches something while Fishing, all Trophy Fish that have their requirements met for are rolled as possible catches, starting from the lowest to highest catch chance. [ Confirm ] If no Trophy Fishes are caught, normal Fishing loot is",
		"§7In case the player has not caught a Gold and/or Diamond tier of a Trophy Fish by then, after 100 and 600 catches of that Trophy Fish, respectively, the next Trophy Fish caught is guaranteed to be that specific type and tier due to Pity.",
		"§8Source: wiki page Trophy Fish",
		"§8Wiki: https://hypixelskyblock.minecraft.wiki/w/Trophy_Fish",
	)
	private val none = HowToGetModel("VOLCANIC_STONEFISH_DIAMOND", emptyList(), emptyList())

	private fun rows(s: String, chars: Int = 26): Int {
		var n = 1; var len = 0
		for (w in s.replace(Regex("§."), "").split(' ')) {
			if (len > 0 && len + 1 + w.length > chars) { n++; len = w.length } else len += (if (len > 0) 1 else 0) + w.length
		}
		return n
	}

	private fun pageLines(): Pair<List<String>, Int> {
		val p = AcquisitionInfo.page(stonefish, "TEXT", none)!!
		val l = mutableListOf("How to obtain"); l.addAll(p.text.map { "§7$it" })
		val link = l.size; l += "> Open wiki page"
		l += listOf("Crafting / recipes", "No recipe produces this item", "Mob drops", "Not dropped by any known mob", "Bazaar / AH", "> No price")
		return l to link
	}

	private val viewH = 160 - 24 - 2

	@Test fun textEntryKeepsItsWikiLinkClickable() {
		val p = AcquisitionInfo.page(stonefish, "TEXT", none)!!
		assertEquals("https://hypixelskyblock.minecraft.wiki/w/Trophy_Fish", p.wikiUrl)
		assertEquals(5, p.text.size)
	}

	@Test fun wrappedContentTallerThanThePageGivesTheRightMaxScroll() {
		val (lines, _) = pageLines()
		val page = ScrollPage(lines.map { rows(it) }, viewH)
		assertEquals(lines.sumOf { rows(it) } * 10, page.contentHeight)
		assertTrue(page.contentHeight > viewH, "content ${page.contentHeight} should overflow $viewH")
		assertEquals(page.contentHeight - viewH, page.maxScroll)
		assertNotNull(page.thumb(0))

		val short = ScrollPage(listOf(1, 2, 1, 1, 1, 1, 1, 1, 1, 1), viewH)
		assertEquals(0, short.maxScroll); assertNull(short.thumb(0))
	}

	@Test fun scrollOffsetIsClamped() {
		val (lines, _) = pageLines()
		val page = ScrollPage(lines.map { rows(it) }, viewH)
		assertEquals(0, page.clamp(-50))
		assertEquals(page.maxScroll, page.clamp(10_000))
		assertEquals(0, page.scrolled(0, 3.0))
		assertEquals(page.maxScroll, page.scrolled(page.maxScroll, -5.0))
		assertEquals(20, page.scrolled(0, -1.0))
	}

	@Test fun linkHitBoxIsCorrectAfterScrolling() {
		val (lines, link) = pageLines()
		val page = ScrollPage(lines.map { rows(it) }, viewH)
		val top = page.tops[link]
		assertTrue(top >= viewH, "link starts below the fold before scrolling")
		assertNull(page.hitBox(link, 0))
		assertNull(page.lineAt((viewH - 1).toDouble(), 0).takeIf { it == link })
		val off = page.clamp(top - 20)
		val box = page.hitBox(link, off)!!
		assertEquals(top - off, box.first)
		assertEquals(link, page.lineAt(box.first.toDouble(), off))
		assertEquals(link, page.lineAt(box.last + 0.5, off))
		assertTrue(page.lineAt(box.first - 0.5, off) != link)
		assertNull(page.lineAt(-1.0, off)); assertNull(page.lineAt(viewH.toDouble(), off))
	}
}
