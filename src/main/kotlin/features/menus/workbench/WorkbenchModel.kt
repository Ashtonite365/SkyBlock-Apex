// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.workbench

import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText

data class WorkbenchSlot(val slot: Int, val name: String, val lore: List<String> = emptyList(),
                         val pane: Boolean? = null, val realItem: Boolean? = null) {
	val cleanName get() = MenuText.clean(name)
	val cleanLore get() = lore.map(MenuText::clean)
	companion object {
		fun of(t: SlotText, pane: Boolean? = null, realItem: Boolean? = null) = WorkbenchSlot(t.slot, t.name, t.lore, pane, realItem)
	}
}

enum class WorkbenchRole {

	FILLER,

	HINT,

	SOCKET,

	ITEM,

	BUTTON;

	val shown get() = this == SOCKET || this == ITEM || this == BUTTON

	val realSlot get() = this == SOCKET || this == ITEM
}

data class WorkbenchTile(val slot: Int, val role: WorkbenchRole, val row: Int, val col: Int, val name: String, val lore: List<String>,

                         val hint: Int? = null)

data class WorkbenchModel(
	val title: String,
	val chestSlots: Int,
	val tiles: List<WorkbenchTile>,

	val rows: Int,
	val cols: Int,
) {
	val sockets get() = tiles.filter { it.role == WorkbenchRole.SOCKET }
	val buttons get() = tiles.filter { it.role == WorkbenchRole.BUTTON || it.role == WorkbenchRole.ITEM }

	val firstSocket get() = sockets.minWithOrNull(compareBy({ it.col }, { it.row }))
	fun tile(slot: Int) = tiles.firstOrNull { it.slot == slot }
}

class SocketMemory {
	private var containerId = Int.MIN_VALUE
	private val seen = mutableSetOf<Int>()
	val sockets: Set<Int> get() = seen

	fun update(containerId: Int, chestSlots: Int, nonEmpty: Set<Int>): Set<Int> {
		if (containerId != this.containerId) { this.containerId = containerId; seen.clear() }
		if (populated(chestSlots, nonEmpty.size)) for (i in 0 until chestSlots) if (i !in nonEmpty) seen += i
		return seen
	}

	companion object {
		fun populated(chestSlots: Int, nonEmpty: Int) = chestSlots > 0 && nonEmpty * 2 > chestSlots
	}
}

object WorkbenchParser {

	fun blank(s: WorkbenchSlot) = s.cleanName.isEmpty()

	fun roles(chestSlots: Int, slots: List<WorkbenchSlot>, seenEmpty: Set<Int> = emptySet()): Map<Int, WorkbenchRole> {
		val by = slots.filter { it.slot in 0 until chestSlots }.associateBy { it.slot }
		val repeats = by.values.filter { !blank(it) && it.pane == null && it.realItem != true }
			.groupBy { it.cleanName to it.cleanLore }.filterValues { it.size >= 2 }.values.flatten().map { it.slot }.toSet()
		return (0 until chestSlots).associateWith { i ->
			val s = by[i]
			when {
				s == null -> WorkbenchRole.SOCKET
				s.realItem == true && s.pane != true -> if (i in seenEmpty) WorkbenchRole.SOCKET else WorkbenchRole.ITEM
				i in seenEmpty -> WorkbenchRole.SOCKET
				blank(s) -> WorkbenchRole.FILLER
				s.pane == true || i in repeats -> WorkbenchRole.HINT
				else -> WorkbenchRole.BUTTON
			}
		}
	}

	fun parse(title: String, chestSlots: Int, slots: List<WorkbenchSlot>, seenEmpty: Set<Int> = emptySet()): WorkbenchModel? {
		if (chestSlots <= 0 || chestSlots % 9 != 0) return null
		val inChest = slots.filter { it.slot in 0 until chestSlots }
		if (inChest.isEmpty()) return null
		val roles = roles(chestSlots, inChest, seenEmpty)
		if (roles.values.none { it == WorkbenchRole.SOCKET }) return null
		val by = inChest.associateBy { it.slot }
		val shown = roles.filterValues { it.shown }.keys.sorted()
		if (shown.isEmpty()) return null
		val top = shown.minOf { it / 9 }; val left = shown.minOf { it % 9 }
		val bottom = shown.maxOf { it / 9 }; val right = shown.maxOf { it % 9 }
		val hints = roles.filterValues { it == WorkbenchRole.HINT }.keys
		val tiles = shown.map { i ->
			val s = by[i]
			val role = roles.getValue(i)
			WorkbenchTile(i, role, i / 9 - top, i % 9 - left, s?.cleanName.orEmpty(), s?.cleanLore.orEmpty(),
				if (role == WorkbenchRole.SOCKET) nearestHint(i, hints) else null)
		}
		return WorkbenchModel(MenuText.clean(title), chestSlots, tiles, bottom - top + 1, right - left + 1)
	}

	fun nearestHint(slot: Int, hints: Set<Int>): Int? = hints
		.filter { it / 9 <= slot / 9 }
		.map { h -> h to maxOf(kotlin.math.abs(h / 9 - slot / 9), kotlin.math.abs(h % 9 - slot % 9)) }
		.filter { it.second <= 2 }
		.minWithOrNull(compareBy({ it.second }, { if (it.first % 9 == slot % 9) 0 else 1 }, { it.first }))?.first

	fun costLines(lore: List<String>): List<String> {
		val clean = lore.map(MenuText::clean)
		val head = clean.indexOfFirst { it.trimEnd(':').equals("Cost", true) }
		if (head >= 0) return clean.drop(head + 1).takeWhile { it.isNotBlank() }
		return clean.filter { it.contains("cost", true) && it.any(Char::isDigit) }
	}
}

object WorkbenchClicks {
	enum class Input { PICKUP, QUICK_MOVE }

	data class Plan(val slot: Int, val button: Int, val input: Input, val silent: Boolean)

	fun plan(slot: Int, packetButton: Int, shift: Boolean, realSlot: Boolean): Plan? {
		if (packetButton !in 0..1 || slot < 0) return null
		return if (realSlot) Plan(slot, packetButton, if (shift) Input.QUICK_MOVE else Input.PICKUP, silent = false)
		else Plan(slot, packetButton, Input.PICKUP, silent = true)
	}

	fun inventorySlot(row: Int, col: Int, chestSlots: Int): Int {
		require(row in 0..3 && col in 0..8)
		return chestSlots + row * 9 + col
	}
}

object WorkbenchTitles {
	private const val SUFFIX = "(?:\\s*(?:[➜>:\\-|#(\\[].*))?\\s*"
	val ANVIL = Regex("^\\s*Anvil$SUFFIX$", RegexOption.IGNORE_CASE)
	val BREWING = Regex("^\\s*Brewing\\s+Stand$SUFFIX$", RegexOption.IGNORE_CASE)
}
