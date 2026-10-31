// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.rei

import dev.ashtonite365.apex.util.compatloader.CompatEntry
import dev.ashtonite365.apex.util.compatloader.CompatSwitch
import dev.ashtonite365.apex.features.items.recipes.ItemListMods

@CompatEntry
object Compat : CompatSwitch {
	override fun enabledHere(): Boolean = ItemListMods.REI.usable()
}
