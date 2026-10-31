// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jei

import me.shedaniel.math.Rectangle
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.PetPlaceholders
import dev.ashtonite365.apex.repo.PetVariants
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter
import dev.ashtonite365.apex.util.ItemId

data class PetRaritiesPage(val base: String)

class PetRaritiesJeiCategory(guiHelper: IGuiHelper) : LayouterCategory<PetRaritiesPage>(
	TYPE, Component.literal("Pet Rarities"), guiHelper.createDrawableItemStack(ItemStack(Items.BONE)), 160, 60,
) {
	companion object {
		val TYPE: IRecipeType<PetRaritiesPage> = IRecipeType.create(Apex.MOD_ID, "pet_rarities", PetRaritiesPage::class.java)

		fun tooltipLines(stack: SBItemStack): List<String> {
			val id = stack.catalogItemId.skyblockId
			val idx = PetVariants.current
			val base = idx.baseOf(id) ?: return emptyList()
			val out = mutableListOf<String>()
			val rarities = idx.rarityNames(id)
			if (rarities.size > 1) out += "§8Rarities: " + rarities.joinToString(", ") { it.lowercase().replaceFirstChar(Char::uppercase) } + " (Pet Rarities page)"
			val rarity = PetVariants.rarityOf(id)
			val info = runCatching { SafeRepoConstants.petNumbers()[base]?.get(rarity) }.getOrNull()
			if (info == null && dev.ashtonite365.apex.repo.RepoManager.getPotentialStubPetData(stack.catalogItemId) != null) out += PetPlaceholders.NO_LEVEL_DATA_NOTE
			return out
		}
	}

	override fun allRecipes(): List<PetRaritiesPage> = PetVariants.current.variants.keys.map { PetRaritiesPage(it) }

	override fun layout(recipe: PetRaritiesPage, bounds: Rectangle, layouter: RecipeLayouter) {
		val ids = PetVariants.current.variants[recipe.base] ?: return
		val stacks = ids.map { SBItemStack(ItemId(it)) }
		layouter.cyclingSlotAt(bounds.minX + 4, bounds.minY + 4, stacks, RecipeLayouter.SlotRole.BIG_OUTPUT)
		layouter.placeLabel(bounds.minX + 32, bounds.minY + 8, Component.literal("${ids.size} rarities §8(cycling)"))
		stacks.forEachIndexed { i, s ->
			layouter.itemSlotAt(bounds.minX + 4 + i * 18, bounds.minY + 34, s, RecipeLayouter.SlotRole.SMALL_OUTPUT)
		}
	}
}
