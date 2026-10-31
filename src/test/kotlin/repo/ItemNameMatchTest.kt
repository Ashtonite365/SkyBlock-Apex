// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.repo.ItemNameMatch

class ItemNameMatchTest {
	private val names = mapOf(
		"DIAMOND" to "§fDiamond", "ENCHANTED_DIAMOND" to "§aEnchanted Diamond", "ENCHANTED_DIAMOND_BLOCK" to "§9Enchanted Diamond Block",
		"HYPERION" to "§dHyperion", "ICE" to "§fIce", "MELON_DICER_2" to "§9Melon Dicer Mk. II", "INFERNAL_KUUDRA_KEY" to "§5Infernal Kuudra Key",
	)
	private fun best(name: String, mangled: Boolean) = ItemNameMatch.bestMatch(names.keys, name, mangled) { listOf(names[it]!!) }

	@Test fun exactMatchesOnlyTheWholeName() {
		assertEquals("ENCHANTED_DIAMOND", best("Enchanted Diamond", false))
		assertEquals("DIAMOND", best("§fDiamond", false))
		assertNull(best("Enchanted Diamond x64", false))
		assertNull(best("Bountiful Melon Dicer Mk. II", false))
		assertEquals("INFERNAL_KUUDRA_KEY", best("Infernal Kuudra Key", false))
	}

	@Test fun mangledFindsTheLongestWholeWordName() {
		assertEquals("HYPERION", best("Heroic Hyperion ✪✪✪✪✪", true))
		assertEquals("ENCHANTED_DIAMOND_BLOCK", best("64x Enchanted Diamond Block", true))
		assertEquals("ENCHANTED_DIAMOND", best("Enchanted Diamond x3", true))
		assertEquals("MELON_DICER_2", best("Bountiful Melon Dicer Mk. II", true))
	}

	@Test fun mangledNeedsWholeWords() {
		assertFalse(ItemNameMatch.nameMatches("Price Tag", "Ice", true))
		assertTrue(ItemNameMatch.nameMatches("Packed Ice", "Ice", true))
		assertNull(best("Diamonds", true))
	}

	@Test fun exactIsNotContains() {

		assertFalse(ItemNameMatch.nameMatches("Heroic Hyperion", "Hyperion", false))
		assertTrue(ItemNameMatch.nameMatches("Heroic Hyperion", "Hyperion", true))
	}
}
