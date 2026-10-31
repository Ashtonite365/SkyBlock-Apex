package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.catalogItemId

class EnchantedBookCache : io.github.moulberry.repo.IReloadable {
	var byName: Map<String, ItemId> = emptyMap()

	override fun reload(repo: NEURepository) = refill(repo)

	private fun refill(repo: NEURepository) {

		byName = repo.items.items.values.asSequence()
			.mapNotNull { item -> EnchantedBookNames.bookName(item.skyblockItemId, item.displayName, item.lore)?.let { it to item.catalogItemId } }
			.sortedBy { it.second.skyblockId }
			.toList().toMap()
	}
}
