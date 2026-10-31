/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): the item-name matcher of SignSearchScreen, pulled out unchanged so the bazaar
 * menu (features/apexbzmenu) filters with exactly the same rule: every space-separated word of the
 * query must appear in the (colour-stripped, lower-cased) item name.
 */
package dev.ashtonite365.apex.features.auction

object SearchMatcher {

	fun words(query: String): List<String> = query.trim().lowercase().split(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE).filter { it.isNotEmpty() }

	fun matches(name: String, words: List<String>): Boolean {
		val l = name.lowercase()
		return words.all { l.contains(it) }
	}

	fun matches(name: String, query: String): Boolean = matches(name, words(query))

	fun matches(name: String, id: String, words: List<String>): Boolean {
		val l = name.lowercase(); val i = id.lowercase()
		return words.all { l.contains(it) || i.contains(it) }
	}
}
