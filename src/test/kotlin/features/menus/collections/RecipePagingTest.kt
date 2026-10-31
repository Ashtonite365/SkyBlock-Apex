// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.recipebook.*
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class RecipePagingTest {
	private fun rec(n: String) = RealDumpFixtures.load(n).let { RecipeBookParser.parse(it.title, it.slots) } as RecipePage.Category

	@Test fun middlePage() {
		val p = rec("recipes-mining-5")
		assertEquals("Mining", p.category); assertEquals(5 to 10, p.page)
		assertEquals(207.0, p.header!!.current); assertEquals(254.0, p.header!!.max)
		assertEquals(listOf("Previous Page", "Go Back", "Close", "Next Page"), p.nav.map { it.name })
		assertTrue(p.recipes.isNotEmpty() && p.recipes.all { it.unlocked })
	}

	@Test fun lastPage() {
		val p = rec("recipes-farming-8")
		assertEquals(8 to 8, p.page)
		assertEquals(listOf("Previous Page", "Go Back", "Close"), p.nav.map { it.name })
		assertEquals(12, p.lockedCount)
		assertEquals("Requires Sunflower Collection V!", p.recipes.first { !it.unlocked }.reason)
	}

	@Test fun slayerSubPageLast() {
		val p = rec("recipes-voidgloom-2")
		assertEquals("Voidgloom Seraph", p.category); assertEquals(2 to 2, p.page)
		assertEquals(9.0, p.header!!.current); assertEquals(30.0, p.header!!.max)
		assertEquals(2, p.lockedCount); assertTrue(p.subCategories.isEmpty())
		assertTrue(p.nav.any { it.name == "Previous Page" } && p.nav.none { it.name == "Next Page" })
	}

	@Test fun tradesLeftVanilla() {
		val d = RealDumpFixtures.load("trades")
		assertEquals("Trades", d.title)
		assertFalse(RecipeBookParser.ANY.containsMatchIn(d.title))
		assertNull(RecipeBookParser.parse(d.title, d.slots))
		for (t in listOf("(2/10) Mining Recipes", "(4/4) Combat Recipes", "(2/2) Inferno Demonlord Recipes", "Special Recipes"))
			assertTrue(RecipeBookParser.ANY.containsMatchIn(MenuRegistry.strip(t)), t)
	}
}
