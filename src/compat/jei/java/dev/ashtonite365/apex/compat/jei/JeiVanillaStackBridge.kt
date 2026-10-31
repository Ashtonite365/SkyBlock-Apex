// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.jei

import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.recipe.IFocus
import mezz.jei.api.recipe.IFocusFactory
import mezz.jei.api.recipe.IRecipeManager
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin
import mezz.jei.api.recipe.types.IRecipeType
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.SkyblockStackLookup
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

class JeiVanillaStackBridge(private val types: () -> List<IRecipeType<*>>) : IRecipeManagerPlugin {
	var recipeManager: IRecipeManager? = null
	var focusFactory: IFocusFactory? = null
	private val logged = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

	private fun sbFocus(focus: IFocus<*>): IFocus<SBItemStack>? {
		val stack = focus.checkedCast(VanillaTypes.ITEM_STACK).orElse(null)?.typedValue?.ingredient ?: return null
		val ff = focusFactory ?: return null
		val id = resolve(stack) ?: return null
		return ff.createFocus(focus.role, SBItemIngredientType, SBItemStack(id))
	}

	private fun resolve(stack: ItemStack): dev.ashtonite365.apex.util.ItemId? = runCatching {
		val acc = stack.view()
		val name = acc.displayNameAccordingToNbt.string
		val id = SkyblockStackLookup.resolveLive(acc.repoItemId?.skyblockId, name)
		if (logged.add(name)) Apex.logger.info("[ItemList] vanilla stack '$name' (id ${acc.repoItemId?.skyblockId ?: "-"}) -> ${id?.skyblockId ?: "no SkyBlock item"}")
		id
	}.getOrNull()

	private fun <T : Any> lookup(type: IRecipeType<T>, focus: IFocus<SBItemStack>): List<T> {
		val rm = recipeManager ?: return listOf()
		return runCatching { rm.createRecipeLookup<T>(type).limitFocus(listOf(focus)).get().toList() }.getOrDefault(listOf())
	}

	override fun <V : Any> getRecipeTypes(focus: IFocus<V>): List<IRecipeType<*>> {
		val f = sbFocus(focus) ?: return listOf()
		@Suppress("UNCHECKED_CAST")
		return types().filter { lookup(it as IRecipeType<Any>, f).isNotEmpty() }
	}

	override fun <T : Any, V : Any> getRecipes(recipeType: IRecipeType<T>, focus: IFocus<V>): List<T> {
		val f = sbFocus(focus) ?: return listOf()
		if (types().none { it.uid == recipeType.uid }) return listOf()
		return lookup(recipeType, f)
	}

	override fun <T : Any> getRecipes(recipeType: IRecipeType<T>): List<T> = listOf()
}
