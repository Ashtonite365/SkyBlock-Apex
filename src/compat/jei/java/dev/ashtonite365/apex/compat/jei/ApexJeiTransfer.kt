/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * SkyBlock Apex: JEI '+' on the "SkyBlock Crafting" category (apex:crafting_recipe) hands the EXACT recipe
 * shown (its output and 3x3 layout) to the Apex Craft Item screen ([CraftHandoff]); /craft is sent once if that
 * window is not open. Loaded only with JEI (isolated jei source set, jei_mod_plugin entrypoint).
 */
package dev.ashtonite365.apex.compat.jei

import io.github.moulberry.repo.data.NEUCraftingRecipe
import java.util.Optional
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.recipe.transfer.IRecipeTransferError
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler
import mezz.jei.api.recipe.types.IRecipeType
import mezz.jei.api.registration.IRecipeTransferRegistration
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.MenuType
import dev.ashtonite365.apex.features.menus.craft.CraftHandoff
import dev.ashtonite365.apex.features.menus.craft.CraftRequest

class ApexJeiTransfer<C : AbstractContainerMenu>(
	private val container: Class<C>,
	private val type: IRecipeType<GenericJeiRecipe<NEUCraftingRecipe>>,
) : IRecipeTransferHandler<C, GenericJeiRecipe<NEUCraftingRecipe>> {
	override fun getContainerClass(): Class<out C> = container
	override fun getMenuType(): Optional<MenuType<C>> = Optional.empty()
	override fun getRecipeType(): IRecipeType<GenericJeiRecipe<NEUCraftingRecipe>> = type

	override fun transferRecipe(
		container: C, recipe: GenericJeiRecipe<NEUCraftingRecipe>, recipeSlots: IRecipeSlotsView,
		player: Player, maxTransfer: Boolean, doTransfer: Boolean,
	): IRecipeTransferError? {
		if (doTransfer) CraftHandoff.request(CraftRequest.of(recipe.recipe))
		return null
	}

	companion object {

		fun register(registration: IRecipeTransferRegistration, type: IRecipeType<GenericJeiRecipe<NEUCraftingRecipe>>) {
			registration.addRecipeTransferHandler(ApexJeiTransfer(InventoryMenu::class.java, type), type)
			registration.addRecipeTransferHandler(ApexJeiTransfer(ChestMenu::class.java, type), type)
		}
	}
}
