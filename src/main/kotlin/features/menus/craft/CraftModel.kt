// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.craft

import dev.ashtonite365.apex.features.menus.support.SlotText

data class CraftSlot(val slot: Int, val name: String, val lore: List<String>)

data class CraftModel(

	val grid: Map<Int, CraftSlot?>,
	val output: CraftSlot?,
	val quickCraft: List<CraftSlot>,

	val others: List<CraftSlot>,
)

object CraftParser {
	val TITLE = Regex("^" + Regex.escape(CraftSlotMap.TITLE) + "$")

	fun filler(s: SlotText) = s.cleanName.isEmpty()

	fun parse(title: String, slots: List<SlotText>, chestSlots: Int = CraftSlotMap.CHEST_SLOTS): CraftModel? {
		if (!TITLE.matches(title.trim()) || chestSlots != CraftSlotMap.CHEST_SLOTS) return null
		val by = slots.associateBy { it.slot }
		fun cs(i: Int) = by[i]?.takeIf { !filler(it) }?.let { CraftSlot(i, it.cleanName, it.cleanLore) }

		if (CraftSlotMap.GRID.any { by[it]?.let(::filler) == true }) return null
		val known = CraftSlotMap.GRID.toSet() + CraftSlotMap.OUTPUT + CraftSlotMap.QUICK_CRAFT
		return CraftModel(CraftSlotMap.GRID.associateWith(::cs), cs(CraftSlotMap.OUTPUT), CraftSlotMap.QUICK_CRAFT.mapNotNull(::cs),
			slots.filter { it.slot < chestSlots && it.slot !in known && !filler(it) }.map { CraftSlot(it.slot, it.cleanName, it.cleanLore) })
	}
}

object Supercraft {

	fun slot(m: CraftModel): Int? = m.others.filter { it.name.trim() == "Supercraft" }.singleOrNull()?.slot

	fun press(m: CraftModel, click: (Int) -> Unit): Boolean { val s = slot(m) ?: return false; click(s); return true }

	fun cells(inputs: List<Pair<String, Double>>): List<Pair<String, Int>?> =
		inputs.map { (id, amt) -> if (id == "CORE_SENTINEL_EMPTY" || id.isEmpty() || amt <= 0) null else id to amt.toInt() }
}

object CraftSplit {
	fun isMinionTable(slots: List<dev.ashtonite365.apex.features.menus.minion.CraftSlot>) =
		dev.ashtonite365.apex.features.menus.minion.MinionCraftParser.isMinionCraftTable(slots)
}
