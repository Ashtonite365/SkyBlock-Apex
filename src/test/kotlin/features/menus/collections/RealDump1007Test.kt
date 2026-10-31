// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.collections

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.collections.*
import dev.ashtonite365.apex.features.menus.recipebook.*
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class RealDump1007Test {
	private fun coll(n: String) = RealDumpFixtures.load(n).let { CollectionsParser.parse(it.title, it.slots) }
	private fun rec(n: String) = RealDumpFixtures.load(n).let { RecipeBookParser.parse(it.title, it.slots) }

	@Test fun boss() {
		val p = coll("boss-collections") as CollPage.Category
		assertEquals("Boss", p.category)
		assertEquals(7.0, p.header!!.unlocked!!.current); assertEquals(8.0, p.header!!.unlocked!!.max)
		val by = p.items.associateBy { it.name }
		assertEquals(listOf("Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron", "Kuudra"), p.items.map { it.name })
		val bonzo = by.getValue("Bonzo")
		assertNull(bonzo.tier); assertEquals(TileState.IN_PROGRESS, bonzo.state)
		assertEquals(5.0, bonzo.progress!!.current); assertEquals(25.0, bonzo.progress!!.max)
		assertEquals(listOf("Red Nose"), bonzo.details)
		assertEquals(TileState.LOCKED, by.getValue("Kuudra").state)
	}

	@Test fun rift() {
		val p = coll("rift-collections") as CollPage.Category
		assertEquals(7, p.items.size); assertTrue(p.items.all { it.state == TileState.LOCKED })
		assertEquals(0.0, p.header!!.unlocked!!.fraction)
	}

	@Test fun craftedMinionsPage2() {
		val p = coll("crafted-minions-2") as CollPage.Minions
		assertEquals(2 to 3, p.page)
		assertEquals(listOf("Previous Page", "Go Back", "Close", "Next Page"), p.nav.map { it.name })

		assertEquals(12, p.minions.first { it.name == "Cocoa Beans Minion" }.maxTier)
		assertEquals(11, p.minions.first { it.name == "Zombie Minion" }.maxTier)
		assertTrue(p.minions.first { it.name == "Creeper Minion" }.complete)
		assertTrue(p.minions.first { it.name == "Flower Minion" }.none)
	}

	@Test fun recipeBookMain() {
		val p = rec("recipe-book-main") as RecipePage.Main
		assertEquals(644.0, p.header!!.current); assertEquals(1000.0, p.header!!.max)
		assertEquals(11, p.categories.size)
		assertTrue(p.nav.map { it.name }.containsAll(listOf("Go Back", "Close", "Trades", "Search Recipes")), p.nav.toString())
		val slayer = p.categories.first { it.name == "Slayer Recipes" }
		assertEquals(43.0, slayer.progress!!.current); assertEquals(139.0, slayer.progress!!.max)
		assertEquals(1.0, p.categories.first { it.name == "Carpentry Recipes" }.progress!!.fraction)
	}

	@Test fun recipePagedCategory() {
		val p = rec("recipes-farming-1") as RecipePage.Category
		assertEquals("Farming", p.category); assertEquals(1 to 8, p.page)
		assertEquals(134.0, p.header!!.current)
		assertTrue(p.subCategories.isEmpty())
		assertTrue(p.recipes.any { it.name == "Enchanted Bread" && it.unlocked })
		assertTrue(p.nav.any { it.name == "Next Page" })
	}

	@Test fun slayerSubCategories() {
		val p = rec("recipes-slayer") as RecipePage.Category
		assertEquals(listOf("Revenant Horror Recipes", "Tarantula Broodfather Recipes", "Sven Packmaster Recipes", "Voidgloom Seraph Recipes", "Inferno Demonlord Recipes"),
			p.subCategories.map { it.name })
		assertEquals(0, p.unlockedCount + p.lockedCount)
		val rev = rec("recipes-revenant") as RecipePage.Category
		assertEquals("Revenant Horror", rev.category)
		assertEquals(17.0, rev.header!!.current); assertEquals(27.0, rev.header!!.max)
		assertEquals(10, rev.lockedCount)
		assertEquals("Requires Zombie Slayer 8!", rev.recipes.first { !it.unlocked }.reason)
		assertTrue(rev.recipes.first { it.name == "Travel Scroll to Hub Crypts" }.unlocked)
		assertEquals(17, rev.unlockedCount)
	}
}
