// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUNpcShopRecipe
import io.github.moulberry.repo.data.NEURecipe
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.catalogItemId

class BetterRepoRecipeCache(vararg val extraProviders: ExtraRecipeProvider)
	: IReloadable {
	var usages: Map<ItemId, Set<NEURecipe>> = emptyMap()
	var recipes: Map<ItemId, Set<NEURecipe>> = emptyMap()

	override fun reload(repository: NEURepository): Unit {
		val using = LinkedHashMap<ItemId, MutableSet<NEURecipe>>()
		val making = LinkedHashMap<ItemId, MutableSet<NEURecipe>>()
		fun MutableMap<ItemId, MutableSet<NEURecipe>>.note(id: ItemId, recipe: NEURecipe) {
			computeIfAbsent(id) { LinkedHashSet() }.add(recipe)
		}
		val extras = extraProviders.flatMap { provider -> provider.provideExtraRecipes() }
		val everything = repository.items.itemsSafe.values.asSequence().flatMap { item -> item.recipes } + extras
		for (recipe in everything) {
			if (recipe is NEUNpcShopRecipe) using.note(recipe.isSoldBy.catalogItemId, recipe)
			for (input in recipe.allInputs) using.note(ItemId(input.itemId), recipe)
			for (output in recipe.allOutputs) making.note(ItemId(output.itemId), recipe)
		}
		usages = using
		recipes = making
	}
}
