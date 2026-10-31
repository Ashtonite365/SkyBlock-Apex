// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.data.Rarity

object PetVariants {
	private val idRe = Regex("^([A-Z0-9_]+);(\\d)$")

	data class Index(val variants: Map<String, List<String>>) {
		private val baseOfId: Map<String, String> = variants.flatMap { (b, ids) -> ids.map { it to b } }.toMap()
		fun baseOf(id: String): String? = baseOfId[id]
		fun representative(base: String): String? = variants[base]?.firstOrNull()
		fun isPet(id: String) = id in baseOfId

		fun uidOf(id: String): String = baseOf(id)?.let { "pet:$it" } ?: id

		fun merged(allIds: Iterable<String>): List<String> = allIds.filter { baseOf(it)?.let { b -> representative(b) == it } ?: true }
		fun variantsOf(id: String): List<String> = baseOf(id)?.let { variants[it] } ?: listOf(id)

		fun cycle(id: String, step: Int = 1): String {
			val v = variantsOf(id); val i = v.indexOf(id)
			return if (i < 0) id else v[Math.floorMod(i + step, v.size)]
		}
		fun rarityNames(id: String): List<String> = variantsOf(id).mapNotNull { rarityOf(it)?.name }
	}

	fun rarityOf(id: String): Rarity? = idRe.matchEntire(id)?.groupValues?.get(2)?.toInt()?.let { Rarity.entries.getOrNull(it) }

	fun index(names: Map<String, String?>, petNumberIds: Set<String>): Index {
		val out = sortedMapOf<String, MutableList<String>>()
		for ((id, name) in names) {
			val m = idRe.matchEntire(id) ?: continue
			val base = m.groupValues[1]
			if (!PetPlaceholders.isStubPet(base, petNumberIds, name)) continue
			out.getOrPut(base) { mutableListOf() }.add(id)
		}
		return Index(out.mapValues { (_, v) -> v.sortedBy { it.substringAfter(';').toInt() } })
	}

	@Volatile private var cached: Triple<Any?, Index, Int?>? = null

	val current: Index get() {
		val items = runCatching { RepoManager.coreRepo.items?.items }.getOrNull()
		cached?.let { if (it.first === items && it.third == items?.size) return it.second }
		val idx = if (items == null) Index(emptyMap())
		else index(items.mapValues { it.value.displayName }, runCatching { SafeRepoConstants.petNumbers().keys }.getOrDefault(emptySet()))
		cached = Triple(items, idx, items?.size)
		return idx
	}
}
