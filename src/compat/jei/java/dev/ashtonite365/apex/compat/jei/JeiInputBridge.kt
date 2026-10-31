// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.jei

import com.google.auto.service.AutoService
import dev.ashtonite365.apex.util.ItemListInputBridge
import dev.ashtonite365.apex.util.compatloader.CompatLoader

@AutoService(ItemListInputBridge::class)
@CompatLoader.RequireMod("jei")
class JeiInputBridge : ItemListInputBridge {
	override fun hasKeyboardFocus(): Boolean =
		JeiHoverSource.runtime?.ingredientListOverlay?.hasKeyboardFocus() == true
}
