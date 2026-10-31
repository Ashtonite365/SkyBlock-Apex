// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.enchant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class EnchantTile(val option: EnchantOption, val applied: Boolean, val conflictsWith: String?) {

	val missing get() = !applied && conflictsWith == null
}

object EnchantConflicts {

	private val ENCHANT_TOKEN = Regex("^([A-Z][A-Za-z'\\- ]*?[A-Za-z']) (?:[IVXLCDM]+|\\d{1,2})$")

	fun idOf(name: String, names: Map<String, String> = emptyMap()): String {
		val n = name.trim().lowercase()
		return names[n] ?: n.replace(Regex("[^a-z0-9]+"), "_").trim('_')
	}

	fun appliedFromLore(lore: List<String>, known: Set<String>): Set<String> {
		val out = LinkedHashSet<String>()
		for (line in lore) for (tok in line.split(",")) {
			val m = ENCHANT_TOKEN.matchEntire(tok.trim()) ?: continue
			val name = m.groupValues[1].trim()
			if (known.isEmpty() || name.lowercase() in known) out += name
		}
		return out
	}

	fun tiles(options: List<EnchantOption>, itemLore: List<String>, pools: List<List<String>>, names: Map<String, String> = emptyMap()): List<EnchantTile> {
		val known = (options.map { it.base.lowercase() } + names.keys + pools.flatten().map { it.replace('_', ' ') }).toSet()
		val onItem = appliedFromLore(itemLore, known)
		val onItemLow = onItem.map { it.lowercase() }.toSet()
		val appliedNames = (onItem + options.filter { it.applied }.map { it.base }).distinctBy { it.lowercase() }

		val poolIds = pools.flatten().toSet()
		fun id(name: String): String = idOf(name, names).let { i -> if (i !in poolIds && "ultimate_$i" in poolIds) "ultimate_$i" else i }
		val appliedIds = appliedNames.associateBy { id(it) }

		val together = appliedIds.keys
		return options.map { o ->
			val applied = o.applied || o.base.lowercase() in onItemLow
			val id = id(o.base)
			val with = if (applied) null else conflictWith(id, appliedIds.keys, pools, together)?.let { appliedIds[it] }
			EnchantTile(o, applied, with)
		}
	}

	fun conflicts(a: String, b: String, pools: List<List<String>>, together: Set<String> = emptySet()): Boolean =
		a != b && !(a in together && b in together) && pools.any { a in it && b in it }

	fun conflictWith(id: String, applied: Collection<String>, pools: List<List<String>>, together: Set<String> = emptySet()): String? =
		applied.firstOrNull { conflicts(id, it, pools, together) }

	data class Split(val missing: List<String>, val conflicts: List<Pair<String, String>>)

	fun splitMissing(pool: List<String>, applied: Collection<String>, pools: List<List<String>>): Split {
		val on = applied.map { it.lowercase() }.toCollection(LinkedHashSet())
		val low = pools.map { g -> g.map { it.lowercase() } }
		val missing = mutableListOf<String>(); val conflicting = mutableListOf<Pair<String, String>>()
		for (e in pool) {
			val id = e.lowercase()
			if (id in on) continue
			val with = conflictWith(id, on, low, on)
			if (with == null) missing += e else conflicting += e to with
		}
		return Split(missing, conflicting)
	}

	fun missingCount(tiles: List<EnchantTile>) = tiles.count { it.missing }

	const val MISSING_FIRST = "Missing Enchantments First"

	fun groups(tiles: List<EnchantTile>, sort: String?): List<Pair<String, List<EnchantTile>>> {
		val applied = "Applied" to tiles.filter { it.applied }
		val missing = "Missing" to tiles.filter { it.missing }
		val conflicts = "Conflicts" to tiles.filter { !it.applied && it.conflictsWith != null }
		val order = if (sort.equals(MISSING_FIRST, true)) listOf(missing, applied, conflicts) else listOf(applied, missing, conflicts)
		return order.filter { it.second.isNotEmpty() }
	}

	fun poolsFrom(json: JsonObject): List<List<String>> =
		json["enchant_pools"]?.jsonArray?.map { g -> g.jsonArray.map { it.jsonPrimitive.content.lowercase() } }.orEmpty()

	fun namesFrom(json: JsonObject): Map<String, String> = buildMap {
		for (group in json.values) for (e in (group as? JsonObject)?.values.orEmpty()) {
			val o = e as? JsonObject ?: continue
			val lore = o["loreName"]?.jsonPrimitive?.content ?: continue
			val nbt = o["nbtName"]?.jsonPrimitive?.content ?: continue
			put(lore.lowercase(), nbt.lowercase())
		}
	}

	const val BUNDLED = "/assets/apex/core-repo/constants/enchants.json"

	private val bundledPools: List<List<String>> by lazy {
		runCatching { EnchantConflicts::class.java.getResource(BUNDLED)?.readText()?.let { poolsFrom(kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject) } }
			.getOrNull().orEmpty()
	}

	fun livePools(): List<List<String>> =
		runCatching { dev.ashtonite365.apex.repo.RepoManager.enchantData.allEnchants?.enchantPools?.map { g -> g.map { it.lowercase() } } }
			.getOrNull()?.takeIf { it.isNotEmpty() } ?: bundledPools

	fun liveNames(): Map<String, String> =
		runCatching { dev.ashtonite365.apex.util.plus.PlusRepoData.loadRepoJson("constants/Enchants.json")?.jsonObject?.let(::namesFrom) }
			.getOrNull().orEmpty()
}
