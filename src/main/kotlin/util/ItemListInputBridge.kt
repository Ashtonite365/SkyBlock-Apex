// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.util.compatloader.CompatLoader

interface ItemListInputBridge {
	fun hasKeyboardFocus(): Boolean

	companion object : CompatLoader<ItemListInputBridge>(ItemListInputBridge::class) {
		fun itemListHasFocus(): Boolean = allValidInstances.any { runCatching { it.hasKeyboardFocus() }.getOrDefault(false) }
	}
}
