// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

import io.github.moulberry.repo.NEURepository
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.repo.SBItemStack

interface GenericRecipeRenderer<T
	: Any> {

	abstract val typ: Class<T>
	abstract val icon: ItemStack
	abstract val title: Component
	abstract val identifier: Identifier

	val displayWidth: Int
		get() = 150
	val displayHeight: Int
		get() = 66

	fun render(
		recipe: T, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?,
	)

	fun getInputs(
		recipe: T,
	): Collection<SBItemStack>
	fun getOutputs(
		recipe: T,
	): Collection<SBItemStack>

	fun findAllRecipes(
		coreRepository: NEURepository,
	): Iterable<T>

	fun discoverExtraRecipes(
		coreRepository: NEURepository, itemStack: SBItemStack, mustBeInOutputs: Boolean,
	): Iterable<T> = emptyList()
}
