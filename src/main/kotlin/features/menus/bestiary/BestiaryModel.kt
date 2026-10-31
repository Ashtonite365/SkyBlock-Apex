// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.bestiary

import kotlinx.serialization.Serializable
import dev.ashtonite365.apex.features.menus.collections.LoreParse
import dev.ashtonite365.apex.features.menus.collections.LoreProgress
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

data class BeastButton(val slot: Int, val name: String, val lore: List<String>)

data class BeastArea(val slot: Int, val name: String, val found: LoreProgress?, val completed: LoreProgress?)

data class BeastFamily(
	val slot: Int, val name: String, val base: String, val tier: Int, val locked: Boolean,
	val kills: Double?, val deaths: Double?,

	val next: LoreProgress?, val nextTier: Int?,
	val overall: LoreProgress?, val maxed: Boolean, val cap: Int?,
	val mobTypes: List<String>, val bonuses: List<String>, val lore: List<String>,
)

data class BeastMilestoneEntry(val slot: Int, val level: Int, val totalTiers: Double?, val next: LoreProgress?, val nextLevel: Int?)

data class BeastToggle(val slot: Int, val name: String, val state: String?, val on: Boolean?)

@Serializable
enum class BeastState { UNLOCKED, IN_PROGRESS, LOCKED }

@Serializable
data class BeastMilestone(val slot: Int, val level: Int, val state: BeastState, val rewards: List<String>, val progress: Pair<Double, Double>?)

sealed interface BestiaryPage {
	val title: String
	val nav: List<BeastButton>

	data class Main(override val title: String, val found: LoreProgress?, val completed: LoreProgress?, val areas: List<BeastArea>,
	                val milestone: BeastMilestoneEntry?, val toggle: BeastToggle?, override val nav: List<BeastButton>) : BestiaryPage

	data class Area(override val title: String, val area: String, val found: LoreProgress?, val completed: LoreProgress?,
	                val subAreas: List<BeastArea>, val families: List<BeastFamily>, val milestone: BeastMilestoneEntry?,
	                val toggle: BeastToggle?, override val nav: List<BeastButton>, val page: Pair<Int, Int>?) : BestiaryPage {
		val unlocked get() = families.count { !it.locked }
		val maxed get() = families.count { it.maxed }
	}

	data class Milestones(override val title: String, val tiles: List<BeastMilestone>, val scrollLeft: Int?, val scrollRight: Int?,
	                      val recenter: Int?, val reminders: BeastToggle?, val next: LoreProgress?, override val nav: List<BeastButton>) : BestiaryPage {
		val current get() = tiles.filter { it.state == BeastState.UNLOCKED }.maxOfOrNull { it.level }
		val inProgress get() = tiles.firstOrNull { it.state == BeastState.IN_PROGRESS }
	}
}

object BestiaryParser {
	private val I = RegexOption.IGNORE_CASE
	val MAIN = Regex("^Bestiary$", I)
	val MILESTONES = Regex("^Bestiary\\s+Milestones$", I)

	val AREA = Regex("^(?:\\(\\d+/\\d+\\)\\s*)?Bestiary\\s*➜\\s*\\S.*$", I)

	private val TIER_PROGRESS = Regex("^Progress to Tier\\s+([IVXLCDM]+|\\d+)$", I)
	private val MILESTONE_NAME = Regex("^Bestiary Milestone\\s+([IVXLCDM]+|\\d+)$", I)
	private val NEXT_MILESTONE = Regex("^Progress to Milestone\\s+([IVXLCDM]+|\\d+)$", I)
	private val OVERALL = Regex("^Overall Progress:\\s*([\\d.,]+)%\\s*(\\(MAX!\\))?\\s*$", I)
	private val CAP = Regex("^Capped at Tier\\s+([IVXLCDM]+|\\d+)$", I)

	fun plain(s: String) = s.filterNot { it in '\uE000'..'\uF8FF' }.replace(dev.ashtonite365.apex.util.SharedRegex.WHITESPACE, " ").trim()
	private fun num(s: String) = s.toIntOrNull() ?: LoreParse.roman(s)
	fun stripPage(t: String) = MenuText.clean(t).replace(Regex("^\\(\\d+/\\d+\\)\\s*|\\s*\\(\\d+/\\d+\\)$"), "")

	fun areaOf(title: String) = stripPage(title).substringAfterLast('➜').trim()

	private val NAV_NAMES = setOf("go back", "close", "next page", "previous page", "scroll left", "scroll right", "re-center")
	private fun isButton(s: SlotText) = s.cleanName.lowercase() in NAV_NAMES || s.cleanName.startsWith("Search", true) || s.cleanName.startsWith("Toggle ", true)

	private fun found(lore: List<String>) = LoreParse.progress(lore, "Families Found")
	private fun completed(lore: List<String>) = LoreParse.progress(lore, "Families Completed")
	private fun isArea(s: SlotText) = found(s.lore) != null

	fun area(s: SlotText) = BeastArea(s.slot, s.cleanName, found(s.lore), completed(s.lore))

	fun toggle(s: SlotText): BeastToggle {

		val line = s.cleanLore.lastOrNull { Regex("^[A-Za-z][\\w ]*:\\s*[A-Z]+$").matches(it) }
		val v = line?.substringAfter(':')?.trim()
		val on = when (v?.uppercase()) { "ON", "SHOWN", "ENABLED", "YES" -> true; "OFF", "HIDDEN", "DISABLED", "NO" -> false; else -> null }
		return BeastToggle(s.slot, s.cleanName, v, on)
	}

	fun milestoneEntry(s: SlotText): BeastMilestoneEntry? {
		val lv = MILESTONE_NAME.matchEntire(s.cleanName)?.let { num(it.groupValues[1]) } ?: return null
		val next = LoreParse.progress(s.lore).firstOrNull { NEXT_MILESTONE.matches(it.label) }
		return BeastMilestoneEntry(s.slot, lv, LoreParse.value(s.lore, "Total Tiers Unlocked"), next,
			next?.let { NEXT_MILESTONE.matchEntire(it.label)?.groupValues?.get(1)?.let(::num) })
	}

	fun family(s: SlotText): BeastFamily {
		val lore = s.cleanLore
		val (base, tier) = LoreParse.splitTier(s.cleanName)
		val locked = lore.any { it.contains("haven't unlocked this Family", true) }
		val next = LoreParse.progress(s.lore).firstOrNull { TIER_PROGRESS.matches(it.label) }
		var overall: LoreProgress? = null; var maxed = false
		lore.forEachIndexed { i, l ->
			OVERALL.matchEntire(l)?.let { m ->
				val fr = lore.getOrNull(i + 1)?.let(LoreParse::fraction)
				overall = LoreProgress("Overall Progress", m.groupValues[1].replace(",", "").toDoubleOrNull(), fr?.first, fr?.second)
				maxed = m.groupValues[2].isNotEmpty()
			}
		}
		val cap = lore.firstNotNullOfOrNull { CAP.matchEntire(it)?.groupValues?.get(1)?.let(::num) }

		val types = lore.firstOrNull { it.startsWith("Mob Type", true) }?.substringAfter(':')?.split(',')
			?.map(::plain)?.filter { it.isNotEmpty() }.orEmpty()
		val bonuses = lore.indexOfFirst { it.endsWith(" Bonuses", true) || Regex("^Tier\\s+\\S+\\s+Rewards$", I).matches(it) }
			.takeIf { it >= 0 }?.let { i -> lore.drop(i + 1).takeWhile { it.isNotBlank() }.map(::plain) }.orEmpty()
		return BeastFamily(s.slot, s.cleanName, base, tier ?: 0, locked, LoreParse.value(s.lore, "Kills"), LoreParse.value(s.lore, "Deaths"),
			next, next?.let { TIER_PROGRESS.matchEntire(it.label)?.groupValues?.get(1)?.let(::num) }, overall, maxed, cap, types, bonuses, lore)
	}

	private fun isFamily(s: SlotText) = s.cleanLore.any { it.startsWith("Kills:", true) || it.contains("unlock it in your Bestiary", true) || it.contains("haven't unlocked this Family", true) }

	private fun header(chest: List<SlotText>, name: String) =
		chest.firstOrNull { it.slot < 9 && (it.cleanName.equals(name, true) || name.length >= 8 && it.cleanName.startsWith(name, true)) && isArea(it) }
			?: chest.firstOrNull { it.slot < 9 && isArea(it) }

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): BestiaryPage? {
		val t = MenuText.clean(title)
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		if (chest.isEmpty()) return null
		val nav = chest.filter(::isButton).map { BeastButton(it.slot, it.cleanName, it.cleanLore) }
		val milestone = chest.firstNotNullOfOrNull(::milestoneEntry)
		val tog = chest.firstOrNull { it.cleanName.startsWith("Toggle ", true) }?.let(::toggle)
		if (MILESTONES.matches(t)) return parseMilestones(t, chest, nav, tog)
		if (MAIN.matches(t)) {
			val head = chest.firstOrNull { it.slot < 9 && it.cleanName.equals("Bestiary", true) }
			val areas = chest.filter { it !== head && !isButton(it) && isArea(it) }.map(::area)
			if (areas.isEmpty()) return null
			return BestiaryPage.Main(t, head?.let { found(it.lore) }, head?.let { completed(it.lore) }, areas, milestone, tog, nav)
		}
		if (!AREA.matches(t) && !isSubAreaPage(t, chest)) return null
		val name = areaOf(t)
		val head = header(chest, name) ?: return null
		val body = chest.filter { it !== head && !isButton(it) && it.slot != milestone?.slot }
		val families = body.filter(::isFamily).map(::family)
		val subs = body.filter { !isFamily(it) && isArea(it) }.map(::area)
		if (families.isEmpty() && subs.isEmpty()) return null
		return BestiaryPage.Area(t, head.cleanName, found(head.lore), completed(head.lore), subs, families, milestone, tog, nav,
			LoreParse.pageOf(t) ?: paging(nav))
	}

	fun isSubAreaPage(title: String, slots: List<SlotText>): Boolean {
		val t = MenuText.clean(title)
		if ('➜' !in t) return false
		val chest = slots.filter { it.slot < 54 && it.cleanName.isNotEmpty() }
		val head = header(chest, areaOf(t)) ?: return false
		return chest.any { it !== head && !isButton(it) && (isFamily(it) || isArea(it)) }
	}

	fun paging(nav: List<BeastButton>): Pair<Int, Int>? {
		fun target(n: String) = nav.firstOrNull { it.name.equals(n, true) }?.let { b ->
			b.lore.firstNotNullOfOrNull { Regex("^Page\\s+(\\d+)", I).find(it)?.groupValues?.get(1)?.toInt() } ?: -1 }
		val next = target("Next Page"); val prev = target("Previous Page")
		return when {
			prev != null && prev > 0 -> (prev + 1) to (if (next == null) prev + 1 else 0)
			next != null && next > 0 -> (next - 1) to 0
			next != null -> 1 to 0
			prev != null -> 2 to 0
			else -> null
		}
	}

	fun milestone(s: SlotText): BeastMilestone? {
		val lv = MILESTONE_NAME.matchEntire(s.cleanName)?.let { num(it.groupValues[1]) } ?: return null
		val lore = s.cleanLore
		val pIdx = lore.indexOfFirst { it.equals("Progress:", true) || it.startsWith("Progress:", true) }
		val progress = if (pIdx >= 0) (LoreParse.fraction(lore[pIdx]) ?: lore.getOrNull(pIdx + 1)?.let(LoreParse::fraction)) else null
		val state = when {
			lore.any { it.equals("UNLOCKED", true) || it.equals("CLAIMED", true) } -> BeastState.UNLOCKED
			pIdx >= 0 -> BeastState.IN_PROGRESS
			else -> BeastState.LOCKED
		}
		val rewards = LoreParse.block(s.lore, "Rewards").map(::plain)
		return BeastMilestone(s.slot, lv, state, rewards, progress)
	}

	private fun parseMilestones(t: String, chest: List<SlotText>, nav: List<BeastButton>, tog: BeastToggle?): BestiaryPage.Milestones? {
		val tiles = chest.filter { !isButton(it) }.mapNotNull(::milestone).sortedBy { it.level }
		fun slot(n: String) = nav.firstOrNull { it.name.equals(n, true) }?.slot
		if (tiles.isEmpty() && slot("Scroll Left") == null && slot("Scroll Right") == null) return null
		val next = chest.firstOrNull { it.cleanName.equals("Bestiary Milestones", true) }
			?.let { h -> LoreParse.progress(h.lore).firstOrNull { NEXT_MILESTONE.matches(it.label) } }
		return BestiaryPage.Milestones(t, tiles, slot("Scroll Left"), slot("Scroll Right"), slot("Re-Center"), tog, next, nav)
	}

	fun remember(seen: Map<Int, BeastMilestone>, live: List<BeastMilestone>): Map<Int, BeastMilestone> =
		seen + live.associateBy { it.level }

	fun ranges(levels: Collection<Int>): List<IntRange> {
		val out = mutableListOf<IntRange>()
		for (l in levels.toSortedSet()) {
			val last = out.lastOrNull()
			if (last != null && l == last.last + 1) out[out.size - 1] = last.first..l else out += l..l
		}
		return out
	}
}
