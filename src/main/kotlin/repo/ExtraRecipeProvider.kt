// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.data.NEURecipe

fun interface ExtraRecipeProvider {

	fun provideExtraRecipes(): Iterable<NEURecipe>
}
