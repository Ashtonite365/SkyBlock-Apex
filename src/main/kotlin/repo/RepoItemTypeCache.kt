// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUItem
import dev.ashtonite365.apex.util.skyblock.ItemType

object RepoItemTypeCache : io.github.moulberry.repo.IReloadable {
	var byItemType: Map<ItemType?, List<NEUItem>> = emptyMap()

	override fun reload(repository: NEURepository): Unit {
		byItemType = repository.items.itemsSafe.values.groupBy { ItemType.fromEscapeCodeLore(it.lore.lastOrNull() ?: "") }
	}
}
