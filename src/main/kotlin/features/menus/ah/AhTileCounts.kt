// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.ah

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.util.data.ManagedConfig

object AhTileCounts {
	@Config
	object TConfig : ManagedConfig("ah-tile-counts", Category.AUCTION_BAZAAR) {
		val show by toggle("show") { true }
	}

	@Serializable data class Entry(val text: String, val at: Long)

	@Serializable data class Store(val profiles: MutableMap<String, MutableMap<String, Entry>> = mutableMapOf())

	@Config
	object Data : DataHolder<Store>(serializer(), "ah-tile-counts", ::Store)

	private const val N = "(\\d[\\d,]*)"
	private val patterns = listOf(
		Regex("$N\\s*(?:of|/)\\s*$N", RegexOption.IGNORE_CASE) to { m: MatchResult -> "${num(m, 1)}/${num(m, 2)}" },
		Regex("(?:you have|you own|you placed|you've placed|owned|total)\\D{0,20}?$N", RegexOption.IGNORE_CASE) to { m -> num(m, 1) },
		Regex("^(?:bids|auctions|items|listings|active|owned|claimable|pending)[^:]{0,24}:\\s*$N", RegexOption.IGNORE_CASE) to { m -> num(m, 1) },
		Regex("$N\\s+(?:active\\s+|pending\\s+|claimable\\s+)?(?:bids?|auctions?|items?|listings?)\\b", RegexOption.IGNORE_CASE) to { m -> num(m, 1) },
	)
	private fun num(m: MatchResult, g: Int) = m.groupValues[g].replace(",", "")

	fun parse(lore: List<String>): String? {
		for ((re, fmt) in patterns) for (line in lore) {
			val l = line.trim()
			if (l.contains("coins", true) || l.contains("price", true)) continue
			re.find(l)?.let { return fmt(it) }
		}
		return null
	}

	fun update(store: Store, profile: String, key: String, lore: List<String>?, now: Long): Pair<String?, Boolean> {
		val map = store.profiles.getOrPut(profile) { mutableMapOf() }
		val fresh = lore?.let(::parse)
		if (fresh != null) {
			val changed = map[key]?.text != fresh
			map[key] = Entry(fresh, now)
			return fresh to changed
		}
		return map[key]?.text to false
	}

	fun key(kind: String, name: String) = kind.lowercase() + ":" + name.trim().lowercase()

	fun countFor(key: String, lore: List<String>?): String? {
		if (!TConfig.show) return null
		val (text, changed) = update(Data.data, SkyBlockSession.profileIdOrZero.toString(), key, lore, System.currentTimeMillis())
		if (changed) Data.markDirty()
		return text
	}
}
