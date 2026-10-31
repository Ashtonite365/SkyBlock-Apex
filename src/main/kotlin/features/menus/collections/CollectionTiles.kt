// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.collections

import dev.ashtonite365.apex.features.menus.collections.TileMenuView.Companion.fmtNum
import dev.ashtonite365.apex.features.menus.collections.TileMenuView.Companion.fmtPct

object CollectionTiles {
	const val GREEN = 0xFF55FF55.toInt(); const val YELLOW = 0xFFFFFF55.toInt(); const val RED = 0xFFFF5555.toInt()
	const val GOLD = 0xFFFFAA00.toInt(); const val GREY = 0xFFAAAAAA.toInt(); const val DIM_BAR = 0xFF555555.toInt()

	fun numeral(t: CollTile): String? = if (t.tier == null) null else t.name.trim().substringAfterLast(' ').takeIf { it != t.name.trim() }

	private fun frac(p: LoreProgress?, fallbackCurrent: Double? = null) =
		p?.let { "${(it.current ?: fallbackCurrent)?.let(::fmtNum) ?: "?"}/${it.max?.let(::fmtNum) ?: "?"}" }

	private fun colour(s: TileState) = when (s) {
		TileState.MAXED -> GOLD; TileState.UNLOCKED -> GREEN; TileState.IN_PROGRESS -> YELLOW; TileState.LOCKED -> RED; TileState.UNKNOWN -> GREY
	}

	fun item(t: CollTile): TileSpec {
		val subs: List<String> = when (t.state) {
			TileState.LOCKED -> listOf("§cnot found yet", "§clocked")
			TileState.MAXED -> listOfNotNull(t.total?.let { "§6maxed · ${fmtNum(it)}" }, "§6maxed")
			else -> listOfNotNull(
				t.progress?.let { p -> listOfNotNull(p.pct?.let(::fmtPct), frac(p, t.total)).joinToString(" · ") }?.takeIf { it.isNotEmpty() },
				frac(t.progress, t.total), t.progress?.pct?.let(::fmtPct), t.total?.let(::fmtNum))
		}
		val badge = numeral(t)
		return TileSpec(t.slot, if (badge != null) t.base else t.name, subs.firstOrNull(), if (t.state == TileState.MAXED) 1.0 else t.progress?.fraction,
			colour(t.state), null, badge, subs.drop(1))
	}

	fun tier(t: CollTile, item: String, headSlot: Int?, itemPath: (Int) -> String?): TileSpec {
		val num = numeral(t) ?: t.tier?.toString() ?: t.name
		val p = t.progress
		val subs: List<String> = when (t.state) {
			TileState.MAXED -> listOf("§6maxed")
			TileState.UNLOCKED -> listOf("§aunlocked", "§a✔")
			TileState.IN_PROGRESS -> listOfNotNull(
				p?.let { listOfNotNull(it.pct?.let(::fmtPct), frac(it)).joinToString(" · ") }?.takeIf { it.isNotEmpty() }?.let { "§e$it" },
				frac(p)?.let { "§e$it" }, p?.pct?.let { "§e" + fmtPct(it) }, "§ein progress")
			TileState.LOCKED -> listOfNotNull(p?.pct?.let { "§7locked · ${fmtPct(it)}" }, "§7locked")
			TileState.UNKNOWN -> listOf("§7?")
		}
		val own = itemPath(t.slot)
		val icon = if (own == null || "stained_glass" in own) headSlot ?: t.slot else t.slot
		val tip = listOf("§f$item ${num}", subs.first()) +
			(p?.let { listOf("§7${it.label}: ${it.pct?.let(::fmtPct) ?: "?"} §8(${frac(it)})") } ?: emptyList()) +
			(if (t.details.isEmpty()) emptyList() else listOf("", "§7Rewards:") + t.details.map { "§a $it" })
		val fraction = when (t.state) {
			TileState.MAXED, TileState.UNLOCKED -> 1.0
			else -> p?.fraction
		}
		return TileSpec(t.slot, "Tier $num", subs.first(), fraction, colour(t.state), tip, null, subs.drop(1), icon,
			if (t.state == TileState.LOCKED) DIM_BAR else null)
	}
}
