// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import net.minecraft.resources.Identifier

object InventoryTexture {
	const val SKYGLASS_PATH = "textures/gui/apex/inventory_skyglass.png"
	const val FORGE_PATH = "textures/gui/apex/inventory_forge.png"

	fun pathFor(state: StyleState): String? = when {
		!state.customMenus || !state.wrapAll -> null
		state.style == Style.FORGE -> FORGE_PATH
		else -> SKYGLASS_PATH
	}

	@JvmStatic
	fun pick(original: Identifier): Identifier =
		pathFor(MenuStyle.state)?.let { Identifier.fromNamespaceAndPath("apex", it) } ?: original

	const val VANILLA_LABEL = 0xFF404040.toInt()

	fun labelColourFor(state: StyleState, original: Int): Int =
		if (original != VANILLA_LABEL || pathFor(state) == null) original else StylePalette.of(state).text

	@JvmStatic
	fun labelColour(original: Int): Int = labelColourFor(MenuStyle.state, original)
}
