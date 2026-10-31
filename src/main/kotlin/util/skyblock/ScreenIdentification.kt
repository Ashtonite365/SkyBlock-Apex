// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object ScreenIdentification {
	private var cachedFor: Screen? = null
	private var cachedType: ScreenType? = null

	fun getType(screen: Screen?): ScreenType? {
		screen ?: return null
		if (cachedFor !== screen) {
			cachedType = ScreenType.entries.firstOrNull { type -> type.detector(screen) }
			cachedFor = screen
		}
		return cachedType
	}
}

private fun ContainerScreen.bottomRowItem(fromEnd: Int): ItemStack = menu.getSlot(menu.rowCount * 9 - fromEnd).item

private fun titleOf(screen: Screen): String = screen.title.bareText

private fun isBazaar(screen: Screen): Boolean {
	if (screen !is ContainerScreen) return false
	if (screen.bottomRowItem(4).view().displayNameAccordingToNbt.bareText == "Manage Orders") return true
	return screen.bottomRowItem(5).view().loreAccordingToNbt.any { line -> line.bareText == "To Bazaar" }
}

enum class ScreenType(val detector: (Screen) -> Boolean)
{
	BAZAAR_ANY(::isBazaar),
	ENCHANTMENT_GUIDE({ screen -> titleOf(screen).endsWith("Enchantments Guide") }),
	SUPER_PAIRS({ screen -> titleOf(screen).startsWith("Superpairs") }),
	EXPERIMENTATION_RNG_METER({ screen -> "Experimentation Table RNG" in titleOf(screen) }),
	DYE_COMPENDIUM({ screen -> "Dye Compendium" in titleOf(screen) }),
}
