/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * APEX-26.3 PORT: JEI replacement for the REI focused-stack provider, so hover features
 * (price graph keybind, item hotkeys, etc.) also work on items in JEI's ingredient list and bookmarks.
 */
package dev.ashtonite365.apex.compat.jei

import com.google.auto.service.AutoService
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.util.HoverStackSource
import dev.ashtonite365.apex.util.compatloader.CompatLoader

@AutoService(HoverStackSource::class)
@CompatLoader.RequireMod("jei")
class JeiHoverSource : HoverStackSource {
	companion object {
		@Volatile
		var runtime: IJeiRuntime? = null
	}

	@OptIn(dev.ashtonite365.apex.repo.ExpensiveItemCacheApi::class, dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	override fun stackUnderMouse(screen: Screen): ItemStack? {
		val rt = runtime ?: return null
		val sb = rt.ingredientListOverlay.getIngredientUnderMouse(SBItemIngredientType)
			?: rt.bookmarkOverlay.getIngredientUnderMouse(SBItemIngredientType)
		if (sb != null) return sb.asImmutableItemStack().rebuildLive()
		val vanilla = rt.ingredientListOverlay.getIngredientUnderMouse(mezz.jei.api.constants.VanillaTypes.ITEM_STACK)
		return vanilla
	}

	override val sourcePriority: Int get() = 1
}
