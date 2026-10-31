// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUIngredient
import io.github.moulberry.repo.data.NEURecipe
import dev.ashtonite365.apex.util.ItemId

class EssenceRecipeProvider
	: IReloadable, ExtraRecipeProvider {

	data class EssenceUpgradeRecipe(val itemId: ItemId, val starCountAfter: Int, val essenceCost: Int,
		val essenceType: String, val extraItems: List<NEUIngredient>) : NEURecipe {
		private val single: NEUIngredient get() = NEUIngredient.fromString("${itemId.skyblockId}:1")
		val essenceIngredient: NEUIngredient = NEUIngredient.fromString(essenceType + ":" + essenceCost)
		val allUpgradeComponents: List<NEUIngredient> = buildList { add(essenceIngredient); addAll(extraItems) }

		override fun getAllInputs(): Collection<NEUIngredient> = buildList { add(single); addAll(allUpgradeComponents) }
		override fun getAllOutputs(): Collection<NEUIngredient> = listOf(single)
	}

	var recipes: List<EssenceUpgradeRecipe> = emptyList(); private set

	override fun provideExtraRecipes(): Iterable<NEURecipe> = this.recipes

	override fun reload(repository: NEURepository): Unit {
		val built = ArrayList<EssenceUpgradeRecipe>()
		for ((coreId, costs) in SafeRepoConstants.essenceCosts(repository)) {

			val perStar = costs?.essenceCosts ?: continue
			val essenceId = "ESSENCE_" + costs.type.uppercase()
			for ((stars, amount) in perStar) {
				val extras = costs.itemCosts?.get(stars).orEmpty().map(NEUIngredient::fromString)
				built += EssenceUpgradeRecipe(ItemId(coreId), stars, amount, essenceId, extras)
			}
		}
		recipes = built
	}
}
