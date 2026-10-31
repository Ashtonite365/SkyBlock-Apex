// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.recipebook.*
import dev.ashtonite365.apex.features.menus.support.SlotText

class RecipeBookTest {
	private fun s(slot: Int, name: String, vararg lore: String) = SlotText(slot, name, lore.toList())

	@Test fun main() {
		val p = RecipeBookParser.parse("Recipe Book", listOf(
			s(4, "Recipe Book", "Recipes Unlocked: 63%", "        644/1k"),
			s(20, "Farming Recipes", "View all of the Farming Recipes", "", "Recipes Unlocked: 80%", "    40/50", "", "Click to view!"),
			s(21, "Mining Recipes", "Recipes Unlocked: 50%", "    10/20"),
			s(48, "Go Back", "To SkyBlock Menu"), s(49, "Close"))) as RecipePage.Main
		assertEquals(644.0, p.header!!.current); assertEquals(1000.0, p.header!!.max)
		assertEquals(listOf("Farming Recipes", "Mining Recipes"), p.categories.map { it.name })
		assertEquals(0.8, p.categories[0].progress!!.fraction!!, 1e-9)
		assertEquals(2, p.nav.size)
	}

	@Test fun category() {
		val p = RecipeBookParser.parse("(1/2) Farming Recipes", listOf(
			s(10, "Enchanted Bread", "", "Click to view recipe!"),
			s(11, "???", "Unlock this recipe by reaching", "Wheat V."),
			s(12, "Hay Bale", "Requires Wheat Collection II"),
			s(53, "Next Page", "Page 2"))) as RecipePage.Category
		assertEquals("Farming", p.category); assertEquals(1 to 2, p.page)
		assertEquals(listOf(true, false, false), p.recipes.map { it.unlocked })
		assertEquals(1, p.unlockedCount)
	}

	@Test fun titles() {
		assertTrue(RecipeBookParser.ANY.containsMatchIn("Recipe Book"))
		assertTrue(RecipeBookParser.ANY.containsMatchIn("Farming Recipes"))
		assertTrue(RecipeBookParser.ANY.containsMatchIn("(2/3) Special Recipes"))
		assertFalse(RecipeBookParser.ANY.containsMatchIn("Quartz Minion Recipes"))
		assertFalse(RecipeBookParser.ANY.containsMatchIn("Craft Item"))
	}
}
