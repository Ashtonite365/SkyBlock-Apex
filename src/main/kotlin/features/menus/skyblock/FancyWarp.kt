// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

import dev.ashtonite365.apex.features.menus.MenuChain
import dev.ashtonite365.apex.features.misc.WarpMenu
import dev.ashtonite365.apex.util.ApexClickDelay

data class NRect(val x: Double, val y: Double, val w: Double, val h: Double)

data class NPoint(val x: Double, val y: Double)

data class DrawRect(val x: Int, val y: Int, val w: Int, val h: Int) {
	fun map(p: NPoint) = (x + Math.round(p.x * w).toInt()) to (y + Math.round(p.y * h).toInt())
	fun map(r: NRect) = DrawRect(x + Math.round(r.x * w).toInt(), y + Math.round(r.y * h).toInt(),
		Math.round(r.w * w).toInt().coerceAtLeast(1), Math.round(r.h * h).toInt().coerceAtLeast(1))
}

object FancyWarpLayout {

	const val ASPECT = 16.0 / 9.0

	val ISLANDS: Set<String> = setOf("the end", "crimson isle", "deep caverns", "mushroom desert", "gold mine", "dungeon hub", "spider's den", "galatea", "jerry's workshop", "the park", "hub", "the barn", "private island", "the garden", "backwater bayou")

	private val ALIASES = mapOf(
		"skyblock hub" to "hub", "home" to "private island", "your island" to "private island",
		"moonglade marsh" to "galatea", "the farming islands" to "the barn", "crimson isle" to "crimson isle",
		"jerry's workshop" to "jerry's workshop", "winter island" to "jerry's workshop",
	)
	private val WARP_ISLAND = mapOf(
		"home" to "private island", "hub" to "hub", "dungeon_hub" to "dungeon hub", "barn" to "the barn",
		"desert" to "mushroom desert", "park" to "the park", "gold" to "gold mine", "deep" to "deep caverns",
		"spider" to "spider's den", "end" to "the end", "nether" to "crimson isle", "garden" to "the garden",
		"moonglade" to "galatea", "galatea" to "galatea", "bayou" to "backwater bayou", "jerry" to "jerry's workshop",
	)

	val PORTALS: Map<String, NPoint> get() = FastTravelWarpTable.ROWS.filter { it.onImage }.associate { it.id to NPoint(it.fx!!, it.fy!!) }

	fun islandKey(name: String, warps: List<String>): String {
		val n = name.trim().lowercase()
		ALIASES[n]?.let { return it }
		if (n in ISLANDS) return n
		warps.firstNotNullOfOrNull { WARP_ISLAND[it] }?.let { return it }
		return n
	}

	fun onImage(key: String) = key in ISLANDS

	fun portal(warp: String, label: String = warp): NPoint? =
		(FastTravelWarpTable.find(warp) ?: FastTravelWarpTable.find(label))?.takeIf { it.onImage }?.let { NPoint(it.fx!!, it.fy!!) }
}

data class FancyWarpEntry(
	val warp: String, val label: String, val islandKey: String, val island: String, val tileSlot: Int,
	val locked: Boolean, val lore: List<String>, val viaSubmenu: Boolean, val reachable: Boolean = true,
)

data class FancyIsland(val key: String, val name: String, val tileSlot: Int, val locked: Boolean, val here: Boolean, val warps: List<FancyWarpEntry>)

sealed interface WarpPlan {

	data class Direct(val slot: Int) : WarpPlan

	data class Command(val command: String) : WarpPlan
	data class Blocked(val reason: String) : WarpPlan
}

object FancyWarp {

	fun build(m: FastTravelModel, repo: List<WarpMenu.Entry>): List<FancyIsland> = m.tiles.map { t ->
		val key = FancyWarpLayout.islandKey(t.island, t.warps)
		val lockLore = if (t.locked) t.lore.filter { it.isNotBlank() } else emptyList()
		val own = t.warps.map { w ->
			FancyWarpEntry(w, w, key, t.island, t.slot, t.locked, lockLore, viaSubmenu = t.opens)
		}
		val extra = FastTravelParser.repoWarps(t.warps, repo).map { e ->
			val label = e.aliases.firstOrNull() ?: e.warp
			FancyWarpEntry(e.warp, label, key, t.island, t.slot, t.locked, lockLore, viaSubmenu = true, reachable = t.opens)
		}
		FancyIsland(key, t.island, t.slot, t.locked, t.here, own + extra)
	}

	fun clickable(e: FancyWarpEntry) = !e.locked && e.reachable

	fun plan(e: FancyWarpEntry): WarpPlan = when {
		e.locked -> WarpPlan.Blocked("locked")
		!e.reachable -> WarpPlan.Blocked("not offered by this Fast Travel tile")
		!e.viaSubmenu -> WarpPlan.Direct(e.tileSlot)
		FastTravelWarpTable.find(e.warp)?.verified == false -> WarpPlan.Blocked("no known /warp id")
		!WARP_ID.matches(e.warp) -> WarpPlan.Blocked("no known /warp id")
		else -> WarpPlan.Command("/warp ${e.warp.lowercase()}")
	}
	private val WARP_ID = Regex("^[A-Za-z0-9_]+$")

	fun unmarked(islands: List<FancyIsland>): List<String> =
		islands.flatMap { it.warps }.filter { e ->
			val r = FastTravelWarpTable.find(e.warp) ?: FastTravelWarpTable.find(e.label)
			r == null || (!r.onImage && !r.bottomRow)
		}.map { it.warp }.distinct()

	private val LOCK = listOf("Warp not unlocked", "Explore more of SkyBlock", "Island is closed", "Requires", "LOCKED")
	private val WARP = Regex("^/warp\\s+([a-z0-9_]+)$", RegexOption.IGNORE_CASE)
	private val FAST_TRAVEL = Regex("Fast Travel", RegexOption.IGNORE_CASE)

	fun lockedLore(lore: List<String>) = lore.any { l -> LOCK.any { l.contains(it, true) } }

	fun pickSub(labels: List<MenuChain.Label>, warp: String, label: String): MenuChain.Label? =
		labels.firstOrNull { s -> s.lore.any { WARP.matchEntire(it.trim())?.groupValues?.get(1).equals(warp, true) } }
			?: labels.firstOrNull { s -> s.name.removePrefix("Warp to:").trim().let { it.equals(label, true) || it.equals(warp, true) } }

	fun fit(x: Int, y: Int, w: Int, h: Int): DrawRect {
		val byW = (w / FancyWarpLayout.ASPECT).toInt()
		return if (byW <= h) DrawRect(x, y + (h - byW) / 2, w, byW)
		else { val ww = (h * FancyWarpLayout.ASPECT).toInt(); DrawRect(x + (w - ww) / 2, y, ww, h) }
	}

	fun <T> nearest(items: List<T>, pos: (T) -> Pair<Int, Int>, mx: Int, my: Int, radius: Int): T? =
		items.map { it to pos(it) }.filter { (_, p) -> sq(p.first - mx) + sq(p.second - my) <= radius * radius }
			.minByOrNull { (_, p) -> sq(p.first - mx) + sq(p.second - my) }?.first

	fun radius(mapW: Int) = (mapW / 64).coerceIn(4, 12)

	private fun sq(v: Int) = v * v
}
