// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.collections.CollectionsParser
import dev.ashtonite365.apex.features.menus.enchant.EnchantMenu
import dev.ashtonite365.apex.features.menus.enchant.EnchantParser
import dev.ashtonite365.apex.features.menus.minion.MinionCraftParser
import dev.ashtonite365.apex.features.menus.recipebook.RecipeBookParser

class MenuMatchingLogTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun denyListMatchesWholeWordsOnly() {
		assertFalse(MenuRegistry.denied("Enchant Item ➜ Sharpness"))
		for (t in listOf("Enchant Item ➜ Smite", "Enchant Item ➜ Experience", "Enchant Item ➜ Bane of Arthropo",
			"Enchant Item ➜ Knockback", "Enchant Item ➜ Fire Aspect", "Enchant Item ➜ Looting", "Enchant Item ➜ Scavenger",
			"Jumbo Backpack (Slot #7)", "Gemstone Grinder", "Enchant Item"))
			assertFalse(MenuRegistry.denied(t), t)

		assertFalse(MenuRegistry.denied("Sharpshooter"))
		assertFalse(MenuRegistry.denied("Experienced Mining"))
		assertFalse(MenuRegistry.denied("Melodys Bakery"))
		assertFalse(MenuRegistry.denied("Determinal Shop"))

		for (t in listOf("Experimentation Table RNG", "Experiment Over", "Chronomatron (Expert)", "Ultrasequencer (High)", "Superpairs (Metaphysical)",
			"Harp - Hymn to the Joy", "Click in order!", "Correct all the panes!", "Change all to same color!",
			"Select all the red items!", "What starts with: 'A'?", "Click the button on time!", "Navigate the maze!", "Terminal"))
			assertTrue(MenuRegistry.denied(t), t)
		assertFalse(MenuRegistry.denied("Melody ♫"))

		assertFalse(MenuRegistry.denied("Experimentation Table"))
	}

	@Test fun enchantPickerMatchesLevelPagesStayVanilla() {
		MenuRegistry.register(EnchantMenu)
		assertSame(EnchantMenu, MenuRegistry.match("Enchant Item"))
		assertTrue(EnchantParser.TITLE.matches("Enchant Item"))
		for (t in listOf("Enchant Item ➜ Sharpness", "Enchant Item ➜ Smite", "Enchant Item ➜ Bane of Arthropo")) {
			assertNull(MenuRegistry.match(t), t)
			assertFalse(MenuRegistry.denied(t), t)
		}
	}

	@Test fun truncatedTitlesHelper() {
		assertEquals(31, "Enchant Item ➜ Bane of Arthropo".length)
		assertTrue(MenuTitles.maybeCut("Enchant Item ➜ Bane of Arthropo"))
		val r = Regex("^${MenuTitles.cutWord("Recipes")}$")
		for (s in listOf("R", "Re", "Recip", "Recipes")) assertTrue(r.matches(s), s)
		assertFalse(r.matches("Recipesx")); assertFalse(r.matches("ecipes"))

		assertTrue(RecipeBookParser.CATEGORY.containsMatchIn("(1/2) Inferno Demonlord Recipes"))

		val cutCollection = "Enchanted Brown Mushroom Collec"
		assertEquals(31, cutCollection.length)
		assertTrue(MenuTitles.cutTail("[^➜]+", "Collection").matches(cutCollection))

		assertTrue(CollectionsParser.ITEM_TITLES.containsMatchIn(cutCollection))
		assertFalse(CollectionsParser.PAGES.containsMatchIn(cutCollection))
		assertFalse(MenuTitles.cutTail("[^➜]+", "Collection").matches("Enchanted Something Collecti"))

		assertFalse(MenuTitles.cutTail("[^➜]+", "Collection").matches("Wheat Collecti"))

		assertTrue(MinionCraftParser.TITLE_HINT.matches("Cobblestone Minion IX"))
		assertTrue(MinionCraftParser.TITLE_HINT.matches("Enchanted Something Long Name Mi"))
		assertFalse(MinionCraftParser.TITLE_HINT.matches("Safari Milestones ➜ Icy Biome M"))
	}
}
