// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items.wiki

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.wiki.ItemRef
import dev.ashtonite365.apex.features.items.wiki.ItemWikiResolver
import dev.ashtonite365.apex.features.items.wiki.WikiRepoView
import dev.ashtonite365.apex.features.items.wiki.WikiTitles

class ItemWikiBundleTest {
	private fun lines(name: String) = javaClass.getResource("/apex-fixtures/item-wiki/$name")!!.readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
	private val rows = lines("bundle-items.tsv")
	private val titles = lines("wiki-titles.tsv").associate { WikiTitles.normalise(it[0]) to Pair(it[1] == "1", it.getOrNull(2) == "1") }
	private val byId = rows.associateBy { it[0] }
	private val repo = object : WikiRepoView {
		override fun ids() = byId.keys
		override fun wikiUrl(id: String) = byId[id]?.getOrNull(2)?.takeIf { it.isNotEmpty() }
		override fun displayName(id: String) = byId[id]?.getOrNull(1)
	}

	private val knownMissing = setOf(
		"BOOPFISH_HELMET", "COCOA_NET", "COTTON_CANDY", "CRITTER_BALL", "DUNGEON_GOLDEN_KEY", "FESTIVE_FRISBEE",
		"HARPOON_LAUNCHER", "HOSTAGE_COMPASS", "LEAVE_CHALLENGE_BUTTON", "MILK_COOKIES", "PROTEIN_BAR",
		"REANIMATED_PAINTING", "REMOTE_TIME_CHAMBER_REMOTE", "STORAGE_MINECART", "SWAPPABLE_PREVIEW", "SYPHONER", "WILD_BERRY",
	)

	@Test fun everyBundleItemLandsOnARealPage() {
		val r = ItemWikiResolver(repo)
		val unknown = mutableListOf<String>(); val missing = mutableSetOf<String>(); val disambig = mutableSetOf<String>()
		var ok = 0
		for (row in rows) {
			val id = row[0]
			val t = r.resolve(ItemRef(id, row[1], listOfNotNull(row.getOrNull(4)?.takeIf { it.isNotEmpty() }), isBook = row.getOrNull(3) == "enchanted_book"))
			if (t == null) { missing += id; continue }
			val cands = listOf(t.title) + t.alternates
			cands.filter { WikiTitles.normalise(it) !in titles }.forEach { unknown += "$id -> $it" }
			val hit = cands.firstOrNull { titles[WikiTitles.normalise(it)]?.first == true }
			if (hit == null) { missing += id; continue }
			if (titles[WikiTitles.normalise(hit)]!!.second) disambig += id
			ok++
		}
		assertEquals(emptyList<String>(), unknown, "titles not checked against the wiki (re-run the audit)")
		assertEquals(knownMissing, missing)
		assertEquals(setOf("COOKIE"), disambig, "only Cookie lands on a disambiguation page (the wiki has no page of its own)")
		assertTrue(ok >= 7937, "resolved $ok of ${rows.size}")
		assertEquals(rows.size, ok + missing.size)
	}

	@Test fun everyEnchantmentLevelSharesOnePage() {
		val r = ItemWikiResolver(repo)
		val byEnchant = rows.filter { it.getOrNull(4)?.isNotEmpty() == true && it[3] == "enchanted_book" && !it[0].startsWith("JERRY;") }
			.groupBy({ it[4].uppercase() }) { r.resolve(ItemRef(it[0], it[1], listOf(it[4]), isBook = true))!!.cacheKey }
		assertTrue(byEnchant.size > 100)
		byEnchant.forEach { (k, pages) -> assertEquals(1, pages.toSet().size, "$k: $pages") }
	}
}
