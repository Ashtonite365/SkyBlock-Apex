// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.stripSectionCodes

object SkyblockStackLookup {

	private val STARS = Regex("[\\s✪➊➋➌➍➎⚚✦]+$")
	private val LEADING_MARKS = Regex("^[\\s⚚✦]+")

	fun cleanName(name: String): String =
		name.stripSectionCodes().replace(STARS, "").replace(LEADING_MARKS, "").replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()

	fun stripReforge(name: String, reforgeNames: Collection<String>): String? {
		val n = cleanName(name)
		val hit = reforgeNames.asSequence().map { it.trim() }.filter { it.isNotEmpty() }
			.filter { n.length > it.length + 1 && n.startsWith("$it ", ignoreCase = true) }
			.maxByOrNull { it.length } ?: return null
		return n.substring(hit.length + 1).trim().takeIf { it.isNotEmpty() }
	}

	fun resolve(
		stackId: String?, displayName: String?, reforgeNames: Collection<String>,
		isKnown: (String) -> Boolean, byName: (String) -> String?,
	): String? {
		stackId?.trim()?.takeIf { it.isNotEmpty() && isKnown(it) }?.let { return it }
		val name = displayName?.let(::cleanName)?.takeIf { it.isNotEmpty() } ?: return null
		byName(name)?.let { return it }
		return stripReforge(name, reforgeNames)?.let(byName)
	}

	fun reforgeNames(): List<String> = ReforgeStore.allReforges.map { it.prefixName }

	fun resolveLive(stackId: String?, displayName: String?): ItemId? = resolve(
		stackId, displayName, reforgeNames(),
		isKnown = { RepoManager.getCoreItem(ItemId(it)) != null },
		byName = { ItemNameLookup.guessItemByName(it, false)?.skyblockId },
	)?.let { ItemId(it) }
}
