// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.jei

import com.google.auto.service.AutoService
import dev.ashtonite365.apex.util.ItemListSearchProvider
import dev.ashtonite365.apex.util.compatloader.CompatLoader

@AutoService(ItemListSearchProvider::class)
@CompatLoader.RequireMod("jei")
class JeiSearchProvider : ItemListSearchProvider {
	override fun currentSearchText(): String? {
		val rt = JeiHoverSource.runtime ?: return null
		if (!rt.ingredientListOverlay.isListDisplayed) return null
		return rt.ingredientFilter.filterText
	}
}
