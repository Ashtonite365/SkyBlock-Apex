// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.itemsSafe
import dev.ashtonite365.apex.util.stripSectionCodes

class ItemNameLookupIndexTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
	}

	private fun allNames(): List<String> {
		RepoManager.initialize()
		val items = RepoManager.coreRepo.items.itemsSafe.values.sortedBy { it.skyblockItemId }
		assertTrue(items.size > 1000, "test repo has ${items.size} items")
		return items.flatMap { ItemNameLookup.getAllNamesForItem(it) }.distinct()
	}

	private fun variants(n: String): List<String> {
		val clean = n.stripSectionCodes()
		val words = clean.trim().split(" ")
		return listOfNotNull(
			"Heroic $clean", "$clean x64", "64x $clean", " $clean ", "\t$clean", "$clean✪✪✪", clean.lowercase(), clean.uppercase(),
			words.first(), words.last(), words.dropLast(1).joinToString(" ").ifEmpty { null }, words.drop(1).joinToString(" ").ifEmpty { null },
			clean.replace(" ", "  "), "§6$clean§r", "§§$clean",
		)
	}

	@Test
	fun exactLookupMatchesTheScanForEveryRepoName() {
		val names = allNames()
		val extra = listOf("", " ", "§a", "Enchanted", "Enchanted Book", "Book", "of", "Mystery Thing", "Feather Falling VI",
			"Sharpness VII", "Ultimate Wise V", "Jacob's Ticket", "Enchanted Hay Bale", "Enchanted Melon Block", "Fine Flour")
		var checked = 0
		var found = 0
		val mismatches = mutableListOf<String>()
		for (n in names + extra) {
			val old = ItemNameLookup.guessItemByNameUncached(n, false)
			val new = ItemNameLookup.guessItemByName(n, false)
			if (old != new) mismatches += "'$n': old=$old new=$new"
			checked++; if (old != null) found++
		}

		for (n in names.filterIndexed { i, _ -> i % 7 == 0 }) for (v in variants(n)) {
			val old = ItemNameLookup.guessItemByNameUncached(v, false)
			val new = ItemNameLookup.guessItemByName(v, false)
			if (old != new) mismatches += "'$v': old=$old new=$new"
			checked++
		}
		println("[gardenhud] exact lookups compared: $checked ($found repo names resolved), mismatches: ${mismatches.size}")
		assertEquals(emptyList<String>(), mismatches.take(20))
		assertTrue(found > names.size / 2)
	}

	@Test
	fun mangledLookupMatchesTheScan() {
		val names = allNames()
		var checked = 0
		val mismatches = mutableListOf<String>()
		for (n in names.filterIndexed { i, _ -> i % 40 == 0 }) for (v in listOf(n) + variants(n)) {
			val old = ItemNameLookup.guessItemByNameUncached(v, true)
			val new = ItemNameLookup.guessItemByName(v, true)
			if (old != new) mismatches += "'$v': old=$old new=$new"
			checked++
		}
		println("[gardenhud] mangled lookups compared: $checked, mismatches: ${mismatches.size}")
		assertEquals(emptyList<String>(), mismatches.take(20))
	}

	@Test
	fun reloadReplacesTheCaches() {
		RepoManager.initialize()
		val before = ItemNameLookup.generation
		val id = ItemNameLookup.guessItemByName("Enchanted Hay Bale", false)
		ItemNameLookup.reload(RepoManager.coreRepo)
		assertNotSame(before, ItemNameLookup.generation)
		val t = System.nanoTime()
		assertEquals(id, ItemNameLookup.guessItemByName("Enchanted Hay Bale", false))
		println("[gardenhud] first exact lookup after reload (index build): %.1f ms".format((System.nanoTime() - t) / 1e6))
	}

	@Test
	fun indexedLookupIsFast() {
		RepoManager.initialize()
		val list = listOf("Enchanted Hay Bale", "Enchanted Cookie", "Enchanted Baked Potato", "Enchanted Sugar Cane", "Enchanted Pumpkin", "Enchanted Melon", "Jacob's Ticket", "Fine Flour")
		list.forEach { ItemNameLookup.guessItemByName(it, false) }
		fun time(f: (String) -> Any?): Double {
			repeat(30) { for (s in list) f(s) }
			val t = System.nanoTime(); val n = 100
			repeat(n) { for (s in list) f(s) }
			return (System.nanoTime() - t) / 1e6 / n
		}
		val old = time { ItemNameLookup.guessItemByNameUncached(it, false) }
		val new = time { ItemNameLookup.guessItemByName(it, false) }
		println("[gardenhud] 8-name list: uncached scan %.3f ms, indexed %.4f ms".format(old, new))
		assertTrue(new < old)
	}
}
