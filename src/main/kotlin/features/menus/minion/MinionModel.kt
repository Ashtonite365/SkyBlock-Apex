// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

import dev.ashtonite365.apex.features.menus.support.SlotText

data class MinionItemSlot(val kind: MinionSlotMap.ItemSlot, val filled: Boolean, val name: String?, val effect: String?)
data class MinionButton(val slot: Int, val name: String, val lore: List<String>)

data class MinionModel(
	val type: String,
	val tierRoman: String,
	val tier: Int,
	val timeBetweenActions: String?,
	val maxStorage: Int?,
	val resourcesGenerated: Long?,
	val itemSlots: List<MinionItemSlot>,

	val storage: Map<Int, String>,
	val collectAll: MinionButton,
	val pickup: MinionButton,
	val nextTier: MinionButton?,

	val upgradeCost: List<String>,
	val maxTier: Boolean,
	val idealLayout: MinionButton?,
	val quickUpgrade: MinionButton?,

	val others: List<MinionButton>,
) {
	val compactors get() = itemSlots.filter { it.filled && it.name?.contains("Compactor") == true }.map { it.name!! }
}

object MinionParser {
	val TITLE = Regex("^(.+) Minion ([IVXL]+)$")

	fun roman(s: String): Int? {
		val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50)
		var total = 0
		for (i in s.indices) {
			val c = v[s[i]] ?: return null
			val n = s.getOrNull(i + 1)?.let { v[it] } ?: 0
			total += if (c < n) -c else c
		}
		return total.takeIf { it > 0 }
	}

	fun filler(s: SlotText) = s.cleanName.isEmpty()

	fun effect(lore: List<String>): String? {
		val i = lore.indexOfFirst { it.isBlank() }.takeIf { it >= 0 } ?: return null
		val para = lore.drop(i + 1).takeWhile { it.isNotBlank() }
		return para.joinToString(" ").takeIf { it.isNotBlank() }
	}

	private val COST = Regex("(?i)(\\bx\\d|\\d[\\d,]*x\\b|coins|cost)")
	fun costLines(lore: List<String>) = lore.filter { COST.containsMatchIn(it) }

	fun parse(title: String, slots: List<SlotText>): MinionModel? {
		val t = TITLE.matchEntire(title.trim()) ?: return null
		val by = slots.associateBy { it.slot }
		for ((slot, name) in MinionSlotMap.REQUIRED) if (by[slot]?.cleanName != name) return null
		val info = by[MinionSlotMap.MINION_INFO]?.takeIf { it.cleanName == title.trim() } ?: return null
		fun line(prefix: String) = info.cleanLore.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
		fun button(slot: Int) = by[slot]?.takeIf { !filler(it) }?.let { MinionButton(slot, it.cleanName, it.cleanLore) }
		val items = MinionSlotMap.ItemSlot.entries.map { k ->
			val s = by[k.slot]
			val filled = s != null && !filler(s) && when {
				k.filledLore != null -> s.cleanLore.firstOrNull() == k.filledLore
				else -> s.cleanName != k.emptyName
			}
			MinionItemSlot(k, filled, s?.cleanName?.takeIf { filled }, s?.takeIf { filled }?.let { effect(it.cleanLore) })
		}
		val next = button(MinionSlotMap.NEXT_TIER)?.takeIf { it.name == "Next Tier" }
		val quick = button(MinionSlotMap.QUICK_UPGRADE)?.takeIf { it.name.contains("Upgrade") }
		val lore = (next?.lore.orEmpty() + quick?.lore.orEmpty())
		return MinionModel(
			type = t.groupValues[1], tierRoman = t.groupValues[2], tier = roman(t.groupValues[2]) ?: return null,
			timeBetweenActions = line("Time Between Actions:"),
			maxStorage = line("Max Storage:")?.replace(",", "")?.toIntOrNull(),
			resourcesGenerated = line("Resources Generated:")?.replace(",", "")?.toLongOrNull(),
			itemSlots = items,
			storage = MinionSlotMap.STORAGE.mapNotNull { i -> by[i]?.takeIf { !filler(it) }?.let { i to it.cleanName } }.toMap(),
			collectAll = button(MinionSlotMap.COLLECT_ALL)!!, pickup = button(MinionSlotMap.PICKUP)!!,
			nextTier = next, upgradeCost = costLines(lore),
			maxTier = lore.any { it.contains("maximum tier") || it.contains("Highest craftable tier") },
			idealLayout = button(MinionSlotMap.IDEAL_LAYOUT)?.takeIf { it.name == "Ideal Layout" },
			quickUpgrade = quick,
			others = slots.filter { it.slot < MinionSlotMap.CHEST_SLOTS && it.slot !in MinionSlotMap.KNOWN && !filler(it) }
				.map { MinionButton(it.slot, it.cleanName, it.cleanLore) },
		)
	}
}

data class IdealLayoutModel(val tiles: List<MinionButton?>, val minion: MinionButton, val goBack: MinionButton?, val close: MinionButton?, val others: List<MinionButton>)

object IdealLayoutParser {
	fun parse(title: String, slots: List<SlotText>): IdealLayoutModel? {
		if (title.trim() != IdealLayoutSlotMap.TITLE) return null
		val by = slots.associateBy { it.slot }
		fun b(i: Int) = by[i]?.takeIf { !MinionParser.filler(it) }?.let { MinionButton(i, it.cleanName, it.cleanLore) }
		val minion = b(IdealLayoutSlotMap.CENTRE)?.takeIf { MinionParser.TITLE.matches(it.name) } ?: return null
		val back = b(IdealLayoutSlotMap.GO_BACK)?.takeIf { it.name == "Go Back" }
		val close = b(IdealLayoutSlotMap.CLOSE)?.takeIf { it.name == "Close" }
		if (back == null && close == null) return null
		val known = IdealLayoutSlotMap.GRID.toSet() + IdealLayoutSlotMap.GO_BACK + IdealLayoutSlotMap.CLOSE
		return IdealLayoutModel(IdealLayoutSlotMap.GRID.map(::b), minion, back, close,
			slots.filter { it.slot < IdealLayoutSlotMap.CHEST_SLOTS && it.slot !in known && !MinionParser.filler(it) }.map { MinionButton(it.slot, it.cleanName, it.cleanLore) })
	}
}

object MinionClicks {
	enum class Kind { PICKUP, QUICK_MOVE }

	fun route(packetButton: Int, shift: Boolean): Pair<Int, Kind> = packetButton to (if (shift) Kind.QUICK_MOVE else Kind.PICKUP)

	fun tileIsRealItem(screen: MinionScreenKind) = screen != MinionScreenKind.RECIPES_LIST

	data class Value(val coins: Double, val priced: Int, val unpriced: Int)
	fun value(stacks: List<Pair<String?, Int>>, price: (String) -> Double?): Value {
		var c = 0.0; var p = 0; var u = 0
		for ((id, n) in stacks) { val v = id?.let(price); if (v == null) u++ else { p++; c += v * n } }
		return Value(c, p, u)
	}
}
