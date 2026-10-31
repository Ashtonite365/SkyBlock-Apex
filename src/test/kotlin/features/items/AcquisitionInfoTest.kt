// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.items

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.items.recipes.AcquisitionInfo
import dev.ashtonite365.apex.features.items.recipes.AcquisitionPage
import dev.ashtonite365.apex.features.items.recipes.HowToGetModel
import dev.ashtonite365.apex.features.items.recipes.RecipeGroup
import dev.ashtonite365.apex.features.items.recipes.SourceKind

class AcquisitionInfoTest {
	private val none = HowToGetModel("PET_SKIN_SHEEP_NEON_GREEN", emptyList(), emptyList())
	private val wiki = "https://hypixelskyblock.minecraft.wiki/w/Neon_Green_Sheep_Skin"

	@Test fun itemWithInfoGetsThePage() {
		assertEquals(AcquisitionPage(emptyList(), wiki), AcquisitionInfo.page(listOf(wiki), "WIKI_URL", none))
		assertEquals(AcquisitionPage(listOf("Obtained from the Pet Skin shop during the Neon event."), wiki),
			AcquisitionInfo.page(listOf("Obtained from the Pet Skin shop during the Neon event.", wiki), "WIKI_URL", none))
	}

	@Test fun itemWithARecipeDropOrShopHasNoPage() {
		assertNull(AcquisitionInfo.page(listOf(wiki), "WIKI_URL", none.copy(recipeGroups = listOf(RecipeGroup(SourceKind.CRAFTING, 1)))))
		assertNull(AcquisitionInfo.page(listOf(wiki), "WIKI_URL", none.copy(recipeGroups = listOf(RecipeGroup(SourceKind.NPC_SHOP, 1)))))
		assertNull(AcquisitionInfo.page(listOf(wiki), "WIKI_URL", none.copy(mobIds = listOf("ZOMBIE"))))
	}

	@Test fun missingInfoGivesNoPage() {
		assertNull(AcquisitionInfo.page(null, null, none))
		assertNull(AcquisitionInfo.page(emptyList(), "", none))
		assertNull(AcquisitionInfo.page(listOf("", "  "), "WIKI_URL", none))
	}

	@Test fun textInfoTypeShowsLinesWithoutLink() {
		val info = listOf("§7Obtained from: §aFire Sale (Community Shop)", "§7Fire Sale date: §fJune 14, 2025", "§7Cost: §f950 SkyBlock Gems")
		assertEquals(AcquisitionPage(info, null), AcquisitionInfo.page(info, "TEXT", none))
		val withSource = listOf("§7Dungeon Reward Chests", "§8Source: wiki page Adaptive Belt")
		assertEquals(AcquisitionPage(withSource, null), AcquisitionInfo.page(withSource, "TEXT", none))
	}
}
