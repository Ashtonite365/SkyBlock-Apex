// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.constants.PetNumbers
import dev.ashtonite365.apex.util.ApexFormatters

object PetPlaceholders {
	private fun fmt(d: Double) = ApexFormatters.withThousands(d, 1)

	fun levelMap(info: PetNumbers, level: Int): Map<String, String> {
		val stats = runCatching { info.interpolatedStatsAtLevel(level) }.getOrNull() ?: return emptyMap()
		val out = mutableMapOf<String, String>()
		stats.otherNumbers?.forEachIndexed { i, v -> out[i.toString()] = fmt(v) }
		stats.statNumbers?.forEach { (k, v) -> out[k] = fmt(v) }
		return out
	}

	const val NO_LEVEL_DATA_NOTE = "§8Level 1 shown: the repo has no pet level data (petnums)"

	fun defaultLevel(info: PetNumbers?): Int = info?.let { runCatching { it.lowLevel }.getOrNull() }?.coerceAtLeast(1) ?: 1

	fun stub(info: PetNumbers?): Map<String, String> {
		val level = defaultLevel(info)
		val out = (info?.let { levelMap(it, level) } ?: emptyMap()).toMutableMap()
		out["LVL"] = level.toString()
		return out
	}

	fun isStubPet(petId: String, petNumberIds: Set<String>, repoDisplayName: String?): Boolean =
		petId in petNumberIds || repoDisplayName?.contains("{LVL}") == true

	fun apply(s: String, map: Map<String, String>): String = map.entries.fold(s) { acc, (k, v) -> acc.replace("{$k}", v) }
}
