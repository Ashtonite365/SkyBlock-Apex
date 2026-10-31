// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.util.compatloader.CompatLoader

interface ItemListSearchProvider {
	fun currentSearchText(): String?

	companion object : CompatLoader<ItemListSearchProvider>(ItemListSearchProvider::class) {
		fun searchText(): String? = allValidInstances.firstNotNullOfOrNull { runCatching { it.currentSearchText() }.getOrNull() }
	}
}
