// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.labelName
import dev.ashtonite365.apex.util.bareText

object SBItemUtil {

	@NeedsComponents
	fun ItemView.getSearchName(): String {
		val shown = labelName.bareText
		val bookEnchant = if ("Enchanted Book" in shown) loreAccordingToNbt.firstOrNull()?.bareText else null
		val levelEnd = if (shown.startsWith("[Lvl")) shown.indexOf(']') else -1
		return when {
			bookEnchant != null -> bookEnchant
			levelEnd > 0 -> shown.drop(levelEnd)
			else -> shown
		}
	}
}
