// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes

import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ItemId

data class AcquisitionPage(val text: List<String>, val wikiUrl: String?)

object AcquisitionInfo {

	fun page(info: List<String>?, infoType: String?, model: HowToGetModel): AcquisitionPage? {
		if (!model.isEmpty) return null
		val lines = info.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

		val linkLine = lines.firstOrNull { linkOf(it) != null }
		val link = linkLine?.let(::linkOf)
		val text = lines.filterNot { it == linkLine }
		if (text.isEmpty() && link == null) return null
		return AcquisitionPage(text, link)
	}

	private val wikiLine = Regex("^(?:Wiki:\\s*)?(https?://\\S+)$")

	fun linkOf(line: String): String? = wikiLine.matchEntire(line.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim())?.groupValues?.get(1)

	fun itemsWithPage(): List<String> =
		runCatching { RepoManager.allItemIds() }.getOrDefault(emptyList()).filter { id -> forItem(id) != null }

	fun forItem(itemId: String): AcquisitionPage? = runCatching {
		val item = RepoManager.getCoreItem(ItemId(itemId)) ?: return null
		page(item.info, item.infoType, HowToGet.forItem(itemId))
	}.getOrNull()
}
