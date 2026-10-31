// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import net.fabricmc.loader.api.FabricLoader

object RecipeViewers {
	val MOD_IDS = listOf("jei", "roughlyenoughitems")

	fun anyLoaded(isLoaded: (String) -> Boolean): Boolean = MOD_IDS.any { runCatching { isLoaded(it) }.getOrDefault(false) }

	val loaded: Boolean by lazy { anyLoaded { FabricLoader.getInstance().isModLoaded(it) } }

	fun hideRecipeBook(toggle: Boolean, viewerLoaded: Boolean) = toggle || viewerLoaded

	@JvmStatic
	fun hideRecipeBookNow(toggle: Boolean): Boolean = hideRecipeBook(toggle, loaded)
}
