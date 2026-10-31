// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.rei

import com.google.auto.service.AutoService
import me.shedaniel.rei.api.client.REIRuntime
import me.shedaniel.rei.api.common.entry.EntryStack
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.features.items.recipes.ItemListMods
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.util.HoverStackSource
import dev.ashtonite365.apex.util.ItemListInputBridge
import dev.ashtonite365.apex.util.ItemListSearchProvider
import dev.ashtonite365.apex.util.compatloader.CompatLoader
import dev.ashtonite365.apex.util.mc.NeedsComponents

object ReiSearch {
	private fun runtime(): REIRuntime? =
		if (!ItemListMods.REI.usable()) null else runCatching { REIRuntime.getInstance() }.getOrNull()

	fun focused(): Boolean = runCatching { runtime()?.searchTextField?.isFocused == true }.getOrDefault(false)

	fun text(): String? = runCatching {
		val rt = runtime() ?: return null
		if (!rt.isOverlayVisible) null else rt.searchTextField?.text
	}.getOrNull()

	fun hoveredEntry(): EntryStack<*>? = runCatching {
		val overlay = runtime()?.overlay?.orElse(null) ?: return null
		overlay.entryList.focusedStack?.takeUnless { it.isEmpty }
			?: overlay.favoritesList.orElse(null)?.focusedStack?.takeUnless { it.isEmpty }
	}.getOrNull()
}

@AutoService(HoverStackSource::class)
@CompatLoader.RequireMod("roughlyenoughitems")
class ReiHoverSource : HoverStackSource {
	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	override fun stackUnderMouse(screen: Screen): ItemStack? {
		val entry = ReiSearch.hoveredEntry() ?: return null
		ReiEntries.skyblockOf(entry)?.let { return it.asImmutableItemStack().rebuildLive() }
		return entry.value as? ItemStack
	}

	override val sourcePriority: Int get() = 1
}

@AutoService(ItemListInputBridge::class)
@CompatLoader.RequireMod("roughlyenoughitems")
class ReiInputBridge : ItemListInputBridge {
	override fun hasKeyboardFocus(): Boolean = ReiSearch.focused()
}

@AutoService(ItemListSearchProvider::class)
@CompatLoader.RequireMod("roughlyenoughitems")
class ReiSearchProvider : ItemListSearchProvider {
	override fun currentSearchText(): String? = ReiSearch.text()
}
