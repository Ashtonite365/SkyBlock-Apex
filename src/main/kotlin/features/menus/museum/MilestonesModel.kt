// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.museum

import kotlinx.serialization.Serializable
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder

enum class MilestoneState { DONE, IN_PROGRESS, LOCKED }

enum class TileColour { GREEN, YELLOW, RED }

@Serializable
data class MilestoneProgress(val xp: Int? = null, val xpMax: Int? = null, val milestone: Int? = null, val milestones: Int? = null,
                             val nextPct: Int? = null)

data class MilestoneTile(val slot: Int, val level: Int, val name: String, val lore: List<String>, val state: MilestoneState?)

data class MilestonesPage(
	val tiles: List<MilestoneTile>,
	val progress: MilestoneProgress?,
	val scrollRightSlot: Int?, val scrollLeftSlot: Int?, val backSlot: Int?, val closeSlot: Int?,
)

object MilestonesParser {
	private val I = RegexOption.IGNORE_CASE
	val TITLE = Regex("^Museum\\s+Milestones$", I)

	val ENTRY_NAME = Regex("^Museum\\s+Milestones$", I)
	const val TOTAL = 40

	private val TOTAL_XP = Regex("^total\\s+xp\\s*:\\s*([\\d,]+)\\s*/\\s*([\\d,]+)$", I)
	private val MILESTONE = Regex("^milestone\\s*:\\s*(\\d+)\\s*/\\s*(\\d+)$", I)
	private val NEXT = Regex("^progress\\s+to\\s+milestone\\s+(\\d+)\\s*:\\s*(\\d+(?:\\.\\d+)?)%$", I)
	private val LEVEL = Regex("^(?:museum\\s+)?milestone\\s+(\\d+)\\b", I)
	private val SCROLL_RIGHT = Regex("^scroll\\s+right$", I)
	private val SCROLL_LEFT = Regex("^scroll\\s+left$", I)
	private val BACK = Regex("^(?:go\\s+)?back\\b", I)
	private val CLOSE = Regex("^close$", I)
	private val DONE_LORE = Regex("\\bunlocked\\b|\\bclaimed\\b|\\bcompleted?\\b|✔", I)
	private val LOCKED_LORE = Regex("\\blocked\\b|not\\s+(?:yet\\s+)?(?:reached|unlocked)", I)
	private val PROGRESS_LORE = Regex("\\bprogress\\b|\\d+\\s*%", I)

	private fun int(s: String) = s.replace(",", "").toIntOrNull()

	fun progress(lore: List<String>): MilestoneProgress? {
		var p = MilestoneProgress()
		var any = false
		for (raw in lore) {
			val l = MenuText.clean(raw).trim()
			TOTAL_XP.matchEntire(l)?.let { p = p.copy(xp = int(it.groupValues[1]), xpMax = int(it.groupValues[2])); any = true }
			MILESTONE.matchEntire(l)?.let { p = p.copy(milestone = int(it.groupValues[1]), milestones = int(it.groupValues[2])); any = true }
			NEXT.matchEntire(l)?.let { p = p.copy(nextPct = it.groupValues[2].toDouble().toInt()); any = true }
		}
		return if (any) p else null
	}

	fun tileState(name: String, lore: List<String>, colour: TileColour?): MilestoneState? {
		val all = (listOf(name) + lore).joinToString("\n") { MenuText.clean(it) }
		return when {
			colour == TileColour.GREEN -> MilestoneState.DONE
			colour == TileColour.YELLOW -> MilestoneState.IN_PROGRESS
			colour == TileColour.RED -> MilestoneState.LOCKED
			LOCKED_LORE.containsMatchIn(all) -> MilestoneState.LOCKED
			DONE_LORE.containsMatchIn(all) -> MilestoneState.DONE
			PROGRESS_LORE.containsMatchIn(all) -> MilestoneState.IN_PROGRESS
			else -> null
		}
	}

	fun parse(title: String, slots: List<SlotText>, colours: Map<Int, TileColour> = emptyMap()): MilestonesPage? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val tiles = mutableListOf<MilestoneTile>()
		var right: Int? = null; var left: Int? = null; var back: Int? = null; var close: Int? = null
		var progress: MilestoneProgress? = null
		for (s in slots) {
			if (s.slot >= 54) continue
			val n = s.cleanName
			when {
				SCROLL_RIGHT.matches(n) -> right = s.slot
				SCROLL_LEFT.matches(n) -> left = s.slot
				BACK.containsMatchIn(n) -> back = s.slot
				CLOSE.matches(n) -> close = s.slot
				else -> {
					LEVEL.find(n)?.let { m ->
						val lv = m.groupValues[1].toInt()
						if (lv in 1..TOTAL) tiles += MilestoneTile(s.slot, lv, n, s.cleanLore, tileState(n, s.cleanLore, colours[s.slot]))
					} ?: progress(s.cleanLore)?.let { if (progress == null) progress = it }
				}
			}
		}
		if (tiles.isEmpty() && right == null && back == null) return null
		return MilestonesPage(tiles.sortedBy { it.level }, progress, right, left, back, close)
	}

	fun states(reached: Int?, live: Map<Int, MilestoneState>): List<MilestoneState> = (1..TOTAL).map { lv ->
		live[lv] ?: when {
			reached == null -> MilestoneState.LOCKED
			lv <= reached -> MilestoneState.DONE
			lv == reached + 1 -> MilestoneState.IN_PROGRESS
			else -> MilestoneState.LOCKED
		}
	}
}

object MilestoneRewards {
	fun xpNeeded(level: Int) = level * 100
	val BASE = listOf("+1% Bits Multiplier", "+2% Bank Interest")
	private val EXTRAS: Map<Int, List<String>> = mapOf(
		3 to listOf("Deluxe Bank Upgrade", "Museum Appraisal Service"),
		5 to listOf("Travel Scroll to Museum"),
		6 to listOf("Super Deluxe Bank Upgrade"),
		8 to listOf("Hex"),
		10 to listOf("Dust the Fish", "Premier Bank Upgrade", "Shen's Auction", "Flower Minion XII"),
		12 to listOf("Collecting Top Hat Emblem"),
		14 to listOf("Luxurious Bank Upgrade"),
		18 to listOf("Palatial Bank Upgrade"),
		20 to listOf("Shen's Special", "Eon the Fish"),
		24 to listOf("Golden Collecting Top Hat Emblem"),
	)
	fun extras(level: Int) = EXTRAS[level].orEmpty()
	fun rewards(level: Int) = BASE + extras(level)

	fun tooltip(level: Int, live: List<String>?): List<String> =
		listOf("Milestone $level · ${xpNeeded(level)} XP") + (live?.takeIf { it.isNotEmpty() } ?: rewards(level).map { "+ $it" })
}

@Config
object MilestoneCache : ProfileSpecificDataHolder<MilestoneCacheData>(MilestoneCacheData.serializer(), "museum-milestones", ::MilestoneCacheData) {
	fun data(): MilestoneCacheData? = runCatching { data }.getOrNull()
	fun recordLore(lore: List<String>) {
		val p = MilestonesParser.progress(lore) ?: return
		val d = data() ?: return
		if (MilestoneCacheData.merge(d, p)) runCatching { markDirty() }
	}
	fun recordPage(page: MilestonesPage) {
		val d = data() ?: return
		var changed = page.progress?.let { MilestoneCacheData.merge(d, it) } ?: false
		for (t in page.tiles) changed = MilestoneCacheData.tile(d, t) || changed
		if (changed) runCatching { markDirty() }
	}
}

@Serializable
data class MilestoneCacheData(var progress: MilestoneProgress? = null,
                              val states: MutableMap<Int, MilestoneState> = mutableMapOf(),
                              val lore: MutableMap<Int, List<String>> = mutableMapOf()) {
	companion object {

		fun merge(d: MilestoneCacheData, p: MilestoneProgress): Boolean {
			val o = d.progress ?: MilestoneProgress()
			val n = MilestoneProgress(p.xp ?: o.xp, p.xpMax ?: o.xpMax, p.milestone ?: o.milestone, p.milestones ?: o.milestones, p.nextPct ?: o.nextPct)
			if (n == d.progress) return false
			d.progress = n; return true
		}
		fun tile(d: MilestoneCacheData, t: MilestoneTile): Boolean {
			var c = false
			t.state?.let { if (d.states[t.level] != it) { d.states[t.level] = it; c = true } }
			if (t.lore.isNotEmpty() && d.lore[t.level] != t.lore) { d.lore[t.level] = t.lore; c = true }
			return c
		}
	}
}

object MilestoneClaim {

	fun liveSlots(page: MilestonesPage) = page.tiles.associate { it.level to it.slot }

	fun decide(level: Int, page: MilestonesPage): Int? = liveSlots(page)[level]
}
