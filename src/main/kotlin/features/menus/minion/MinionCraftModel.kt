// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

data class CraftSlot(val index: Int, val name: String, val id: String?, val count: Int = 1)

data class MinionRef(val base: String, val tier: Int) {

	val id get() = "${base}_$tier"
}

enum class CraftContext { MINION_UPGRADE, MINION_CRAFT, OTHER }

enum class MinionScreenKind {

	MINION_WINDOW,

	TIER_RECIPE,

	RECIPES_LIST,

	OTHER,
}

data class MinionCraftModel(
	val context: CraftContext,

	val centre: MinionRef?,

	val result: MinionRef?,

	val placed: Map<String, Int>,
	val resultSlot: Int,
	val gridSlots: List<Int>,
)

object MinionCraftSlots {

	val GRID = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)
	const val CENTRE = 20

	const val VIEWER_RESULT = 25

	const val TABLE_RESULT = 23

	val QUICK_CRAFT = listOf(16, 25, 34)
	const val SUPERCRAFT = 32
	const val GO_BACK = 48
	const val CLOSE = 49

	const val ALL_TIERS = 50
	const val CHEST_SLOTS = 54
}

object MinionCraftParser {

	val TITLE_HINT = Regex("^.+ Minion(?: [IVXL]+| ${dev.ashtonite365.apex.features.menus.MenuTitles.cutWord("Recipes")})?$|^(?!.*➜).{24,} ${dev.ashtonite365.apex.features.menus.MenuTitles.cutWord("Minio")}$")
	private val NAME = Regex("^(.+?) Minion ([IVXL]+)$")
	private val ID = Regex("^([A-Z0-9_]+_GENERATOR)_(\\d+)$")

	fun minion(s: CraftSlot?): MinionRef? {
		s ?: return null
		s.id?.let { ID.matchEntire(it) }?.let { return MinionRef(it.groupValues[1], it.groupValues[2].toInt()) }
		val m = NAME.matchEntire(s.name.trim()) ?: return null
		return MinionParser.roman(m.groupValues[2])?.let { MinionRef(m.groupValues[1].trim(), it) }
	}

	private val BASE_ONLY = Regex("^(.+?) Minion$")

	fun isViewer(by: Map<Int, CraftSlot>) = by[MinionCraftSlots.TABLE_RESULT]?.name?.trim() == "Crafting Table" ||
		by.values.any { it.name.trim() == "Supercraft" }
	fun resultSlot(by: Map<Int, CraftSlot>) = if (isViewer(by)) MinionCraftSlots.VIEWER_RESULT else MinionCraftSlots.TABLE_RESULT

	fun isMinionCraftTable(slots: List<CraftSlot>): Boolean {
		val by = slots.associateBy { it.index }
		return !isViewer(by) && (minion(by[MinionCraftSlots.TABLE_RESULT]) != null || minion(by[MinionCraftSlots.CENTRE]) != null)
	}

	fun classify(slots: List<CraftSlot>): MinionScreenKind {
		val by = slots.associateBy { it.index }
		if (by[48]?.name?.trim() == "Collect All" && by[53]?.name?.trim() == "Pickup Minion") return MinionScreenKind.MINION_WINDOW
		val tiered = slots.mapNotNull { minion(it) }

		if (tiered.groupBy { it.base }.values.any { l -> l.map { it.tier }.distinct().size >= 3 }) return MinionScreenKind.RECIPES_LIST
		val result = minion(by[resultSlot(by)])
		if (result != null || minion(by[MinionCraftSlots.CENTRE]) != null) return MinionScreenKind.TIER_RECIPE
		return MinionScreenKind.OTHER
	}

	fun allTiersSlot(slots: List<CraftSlot>): Int? =
		slots.firstOrNull { it.index == MinionCraftSlots.ALL_TIERS && BASE_ONLY.matches(it.name.trim()) }?.index

	fun tiers(slots: List<CraftSlot>): List<Pair<Int, MinionRef>> = slots.mapNotNull { s -> minion(s)?.let { s.index to it } }.sortedBy { it.second.tier }

	fun parse(slots: List<CraftSlot>): MinionCraftModel? {
		val by = slots.associateBy { it.index }
		val centre = minion(by[MinionCraftSlots.CENTRE])
		val resultSlot = resultSlot(by)
		val result = minion(by[resultSlot])
		val ctx = when {
			centre != null -> CraftContext.MINION_UPGRADE
			result != null -> CraftContext.MINION_CRAFT
			else -> CraftContext.OTHER
		}
		val placed = MinionCraftSlots.GRID.filter { !(it == MinionCraftSlots.CENTRE && centre != null) }
			.mapNotNull { by[it] }.filter { it.name.isNotBlank() }
			.groupBy { it.id ?: it.name }.mapValues { (_, v) -> v.sumOf { it.count } }
		return MinionCraftModel(ctx, centre, result, placed, resultSlot, MinionCraftSlots.GRID)
	}

	data class Requirement(val name: String, val have: Long, val need: Long) { val ok get() = have >= need }
	private val REQ = Regex("^\\s*([✔✖✓✗])?\\s*([\\d,]+)\\s*/\\s*([\\d,]+)\\s+(.+)$")

	fun requirements(slots: List<CraftSlot>, lore: Map<Int, List<String>>): List<Requirement> {
		val sc = slots.firstOrNull { it.name.trim() == "Supercraft" }?.index ?: MinionCraftSlots.SUPERCRAFT
		return (lore[sc] ?: return emptyList()).mapNotNull { REQ.matchEntire(it) }.map {
			Requirement(it.groupValues[4].trim(), it.groupValues[2].replace(",", "").toLong(), it.groupValues[3].replace(",", "").toLong())
		}
	}

	data class CraftResult(val missing: Long, val have: Long, val need: Long)
	private val NOT_ENOUGH = Regex("^You don't have enough items to craft that tier! You need ([\\d,]+) more\\. \\(([\\d,]+)/([\\d,]+)\\)$")

	fun craftResult(chat: String): CraftResult? = NOT_ENOUGH.matchEntire(chat.trim())?.let { m ->
		val n = m.groupValues.drop(1).map { it.replace(",", "").toLong() }
		CraftResult(n[0], n[1], n[2])
	}

	fun maxTierFromList(slots: List<CraftSlot>): Int? = tiers(slots).maxOfOrNull { it.second.tier }

	fun missing(required: Map<String, Int>, placed: Map<String, Int>): Map<String, Int> =
		required.mapValues { (k, v) -> v - (placed[k] ?: 0) }.filterValues { it > 0 }

	fun isMaxTier(m: MinionRef, maxTiers: Map<String, Int>): Boolean = maxTiers[m.base]?.let { m.tier >= it } ?: false

	fun nextTier(m: MinionRef, maxTiers: Map<String, Int>): Int? = maxTiers[m.base]?.let { if (m.tier < it) m.tier + 1 else null }
}

class WindowScoped<V : Any> {
	private var window: Any? = null
	private var value: V? = null
	fun set(window: Any, v: V) { this.window = window; value = v }
	fun get(window: Any?): V? = value?.takeIf { window != null && window === this.window }
	fun onWindowChange(newWindow: Any?) { if (newWindow == null || newWindow !== window) clear() }
	fun clear() { window = null; value = null }
}
