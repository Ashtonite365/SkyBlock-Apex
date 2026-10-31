package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.data.NEUItem
import java.util.NavigableMap
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.catalogItemId

object ItemNameLookup
	: IReloadable {

	fun getItemNameChunks(name: String): Set<String> =
		name.stripSectionCodes().split(' ').filterNotTo(LinkedHashSet(), String::isBlank)

	private class Index(val nameMap: NavigableMap<String, out Set<ItemId>>) {

		val names = ConcurrentHashMap<ItemId, Set<String>>()

		@Volatile
		private var exact: Map<String, List<ItemId>>? = null

		fun namesOf(id: ItemId): Set<String> =
			names[id] ?: (RepoManager.getCoreItem(id)?.let(::getAllNamesForItem) ?: emptySet()).also { names[id] = it }

		fun exact(): Map<String, List<ItemId>> {
			exact?.let { return it }
			synchronized(this) {
				exact?.let { return it }
				val out = HashMap<String, MutableSet<ItemId>>()
				for (set in nameMap.values) for (id in set) for (n in namesOf(id)) {
					val a = n.stripSectionCodes().trim()
					if (a.isNotEmpty()) out.getOrPut(a, ::mutableSetOf).add(id)
				}
				val built = out.mapValues { (_, ids) -> ItemNameMatch.preferredOrder(ids) { it.skyblockId } }
				exact = built
				return built
			}
		}
	}

	@Volatile
	private var index = Index(TreeMap())

	var nameMap: NavigableMap<String, out Set<ItemId>> = TreeMap<String, Set<ItemId>>()
		set(value) {
			field = value
			index = Index(value)
		}

	val generation: Any get() = index

	override fun reload(repository: NEURepository): Unit {
		val words = TreeMap<String, MutableSet<ItemId>>()
		for (repoItem in repository.items.itemsSafe.values) {
			val id = repoItem.catalogItemId
			for (itemName in getAllNamesForItem(repoItem)) for (word in getItemNameChunks(itemName))
				words.computeIfAbsent(word) { LinkedHashSet() }.add(id)
		}
		nameMap = words
	}

	fun getAllNamesForItem(item: NEUItem): Set<String> = LinkedHashSet<String>().apply {
		add(item.displayName)

		EnchantedBookNames.bookName(item.skyblockItemId, item.displayName, item.lore)?.let { add(it) }
	}

	fun findItemCandidatesByName(name: String): MutableSet<ItemId> = nameMap.let { index ->
		getItemNameChunks(name).flatMapTo(LinkedHashSet()) { word -> index[word].orEmpty() }
	}

	fun guessItemByName(name: String, mayBeMangled: Boolean): ItemId? {
		val cleanName: String = name.stripSectionCodes()
		if (mayBeMangled) return findBestItemFromCandidates(findItemCandidatesByName(cleanName), cleanName, true)
		return exactMatch(index, cleanName)
	}

	private fun exactMatch(idx: Index, cleanName: String): ItemId? {
		val key = cleanName.stripSectionCodes().trim()
		if (key.isEmpty()) return null
		val ids = idx.exact()[key] ?: return null
		val chunks = getItemNameChunks(cleanName)
		return ids.firstOrNull { id -> chunks.any { idx.nameMap[it]?.contains(id) == true } }
	}

	fun guessItemByNameUncached(name: String, mayBeMangled: Boolean): ItemId? {
		val plain = name.stripSectionCodes()
		return ItemNameMatch.bestMatch(
			ItemNameMatch.preferredOrder(findItemCandidatesByName(plain)) { it.skyblockId }, plain, mayBeMangled
		) { c -> RepoManager.getCoreItem(c)?.let(::getAllNamesForItem) ?: emptySet() }
	}

	fun findBestItemFromCandidates(candidates: Iterable<ItemId>, name: String, mayBeMangled: Boolean): ItemId? {
		val idx = index
		return ItemNameMatch.bestMatch(ItemNameMatch.preferredOrder(candidates) { it.skyblockId }, name, mayBeMangled, idx::namesOf)
	}

	init { RepoManager.initialize() }

}

object ItemNameMatch {

	fun nameMatches(expected: String, actual: String, mayBeMangled: Boolean): Boolean {
		val e = expected.stripSectionCodes().trim()
		val a = actual.stripSectionCodes().trim()
		if (a.isEmpty()) return false
		if (!mayBeMangled) return e == a
		return Regex("(^|\\s)" + Regex.escape(a) + "($|\\s)").containsMatchIn(e)
	}

	fun <K> preferredOrder(candidates: Iterable<K>, idOf: (K) -> String): List<K> =
		candidates.sortedWith(compareBy<K>({ idOf(it).startsWith("STARRED_") }, { idOf(it) }))

	fun dungeonVariant(id: String, masterMode: Boolean, exists: (String) -> Boolean): String {
		val plain = id.removePrefix("STARRED_")
		val starred = "STARRED_$plain"
		return when {
			masterMode && exists(starred) -> starred
			!masterMode && plain != id && exists(plain) -> plain
			else -> id
		}
	}

	fun <K> bestMatch(candidates: Iterable<K>, name: String, mayBeMangled: Boolean, namesOf: (K) -> Iterable<String>): K? {
		var bestMatch: K? = null
		var bestLength = -1
		candidates.forEach { candidate ->
			for (n in namesOf(candidate)) {
				if (!nameMatches(name, n, mayBeMangled)) continue
				val len = n.stripSectionCodes().trim().length
				if (len > bestLength) { bestMatch = candidate; bestLength = len }
			}
		}
		return bestMatch
	}

}
