// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.wardrobe

import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds

enum class LoadoutRole(val label: String, val types: List<String>) {
	MISC("Empty", emptyList()),
	NECKLACE("Necklace", listOf("NECKLACE")), CLOAK("Cloak", listOf("CLOAK")), BELT("Belt", listOf("BELT")),
	BRACELET("Gloves/Bracelet", listOf("BRACELET", "GLOVES")),
	HELMET("Helmet", listOf("HELMET")), CHESTPLATE("Chestplate", listOf("CHESTPLATE")), LEGGINGS("Leggings", listOf("LEGGINGS")),
	BOOTS("Boots", listOf("BOOTS")), PET("Pet", listOf("PET"));
}

data class LoadoutCell(val col: Int, val row: Int, val role: LoadoutRole, val slot: Int?, val item: SlotText?, val typeOk: Boolean) {
	val empty get() = item == null
	val name get() = item?.cleanName ?: role.label

	val lore get() = item?.lore ?: emptyList()
	val summary get() = item?.let { LoadoutSummary.line(it) }
}

data class LoadoutCard(val loadout: Loadout, val lore: List<String>, val requirement: List<String>, val setCount: Int, val total: Int)

data class LoadoutPage(val cards: List<LoadoutCard>, val cells: List<LoadoutCell>, val nav: dev.ashtonite365.apex.features.menus.support.PageNav,
                       val extraButtons: List<SlotText>, val missing: List<String>) {
	val active get() = cards.firstOrNull { it.loadout.active }
	fun cell(col: Int, row: Int) = cells.firstOrNull { it.col == col && it.row == row }
	val pet get() = cells.firstOrNull { it.col == 3 }
}

object LoadoutSummary {
	private val ABILITY = Regex("^(?:Ability: .+|.*\\bItem Ability\\b.*)$", RegexOption.IGNORE_CASE)
	private val STAT = Regex("^[A-Z][A-Za-z' ]{1,30}: [+-]?\\d")

	fun line(s: SlotText): String? {
		val l = s.cleanLore
		return l.firstOrNull { ABILITY.matches(it) } ?: l.firstOrNull { STAT.containsMatchIn(it) } ?: l.firstOrNull { it.isNotBlank() }
	}
}

object LoadoutPageParser {

	private val COLUMN_ROLES = listOf(
		List(4) { LoadoutRole.MISC },
		listOf(LoadoutRole.NECKLACE, LoadoutRole.CLOAK, LoadoutRole.BELT, LoadoutRole.BRACELET),
		listOf(LoadoutRole.HELMET, LoadoutRole.CHESTPLATE, LoadoutRole.LEGGINGS, LoadoutRole.BOOTS))

	const val PET_COL = 3
	const val PET_ROW = 1

	fun typeOf(s: SlotText) = s.cleanLore.lastOrNull { it.isNotBlank() }?.uppercase().orEmpty()
	fun typeMatches(role: LoadoutRole, s: SlotText) = role.types.isEmpty() || typeOf(s).split(' ').any { it in role.types }

	fun parse(title: String, slots: List<SlotText>, log: (String) -> Unit = {}): LoadoutPage? {
		val base = LoadoutParser.parse(title, slots) ?: return null
		val by = slots.filter { it.slot < 54 }.associateBy { it.slot }
		val firstRow = LoadoutKeybinds.Logic.loadoutSlots.minOf { it } / 9
		fun slotOf(col: Int, row: Int) = (firstRow + row) * 9 + col
		val missing = mutableListOf<String>()
		val cells = mutableListOf<LoadoutCell>()
		for ((col, roles) in COLUMN_ROLES.withIndex()) for ((row, role) in roles.withIndex()) cells += cell(col, row, role, slotOf(col, row), by, missing, log)
		cells += cell(PET_COL, PET_ROW, LoadoutRole.PET, slotOf(PET_COL, PET_ROW), by, missing, log)
		val cards = base.loadouts.map { l ->
			val s = by[l.slot]!!
			val set = s.cleanLore.filter { it.contains(": ") }
			LoadoutCard(l, s.lore, if (l.locked) s.lore.filter { it.isNotBlank() } else emptyList(), set.count { !it.endsWith(": None") }, set.size)
		}
		val used = cells.mapNotNull { it.slot }.toSet() + LoadoutKeybinds.Logic.loadoutSlots.toSet() +
			listOfNotNull(base.nav.prev, base.nav.next, base.nav.back, base.nav.close)
		val extra = slots.filter { it.slot in 45 until 54 && it.slot !in used && !TileParse.isFiller(it) }
		return LoadoutPage(cards, cells, base.nav, extra, missing)
	}

	private fun cell(col: Int, row: Int, role: LoadoutRole, slot: Int, by: Map<Int, SlotText>, missing: MutableList<String>, log: (String) -> Unit): LoadoutCell {
		val s = by[slot]
		if (s == null) { missing += role.label; log("[Loadouts] role ${role.label} (col $col row $row, slot $slot) not on screen: placeholder"); return LoadoutCell(col, row, role, null, null, false) }
		if (TileParse.isFiller(s)) return LoadoutCell(col, row, role, slot, null, true)
		val ok = typeMatches(role, s)
		if (!ok) log("[Loadouts] slot $slot lore type '${typeOf(s)}' does not match role ${role.label}; shown as read")
		return LoadoutCell(col, row, role, slot, s, ok)
	}
}

data class LRect(val x: Int, val y: Int, val w: Int, val h: Int) {
	fun contains(px: Int, py: Int) = px >= x && px < x + w && py >= y && py < y + h
	fun inside(o: LRect) = x >= o.x && y >= o.y && x + w <= o.x + o.w && y + h <= o.y + o.h
}

class LoadoutLayout(val frame: LRect) {
	companion object { const val HEADER = 22; const val FOOTER = 22; const val GAP = 3; const val CARD_H = 30; const val DETAILS_H = 24 }
	val body = LRect(frame.x + 6, frame.y + HEADER, frame.w - 12, frame.h - HEADER - FOOTER)
	val cellH = (body.h - 3 * GAP) / 4
	val cellW = minOf(64, ((body.w * 52 / 100) - 3 * GAP) / 4)
	val left = LRect(body.x, body.y, 4 * cellW + 3 * GAP, body.h)
	val right = LRect(left.x + left.w + 8, body.y, body.x + body.w - (left.x + left.w + 8), body.h)
	val cardsArea = LRect(right.x, right.y + 12, right.w, right.h - 12 - DETAILS_H - GAP)
	val details = LRect(right.x, right.y + right.h - DETAILS_H, right.w, DETAILS_H)
	val footer = LRect(frame.x + 4, frame.y + frame.h - 18, frame.w - 8, 14)
	fun cell(col: Int, row: Int) = LRect(left.x + col * (cellW + GAP), left.y + row * (cellH + GAP), cellW, cellH)
	val cardCols get() = maxOf(1, (cardsArea.w + GAP) / (96 + GAP))
	val cardW get() = (cardsArea.w - (cardCols - 1) * GAP) / cardCols
	fun card(i: Int, scroll: Int = 0) = LRect(cardsArea.x + (i % cardCols) * (cardW + GAP), cardsArea.y + (i / cardCols) * (CARD_H + GAP) - scroll, cardW, CARD_H)
	fun cardsHeight(n: Int) = ((n + cardCols - 1) / cardCols) * (CARD_H + GAP)
}

class LoadoutController(private val click: (slot: Int, button: Int) -> Unit, private val log: (String) -> Unit = {}) {
	var page: LoadoutPage? = null; private set
	var updates = 0; private set
	fun update(title: String, slots: List<SlotText>) { page = LoadoutPageParser.parse(title, slots, log); updates++ }
	fun clickCard(index: Int, button: Int = 0): Boolean {
		val c = page?.cards?.getOrNull(index) ?: return false
		click(c.loadout.slot, if (button == 1) 1 else 0); return true
	}
	fun clickAt(layout: LoadoutLayout, mx: Int, my: Int, button: Int = 0, scroll: Int = 0): Boolean {
		val p = page ?: return false
		if (!layout.cardsArea.contains(mx, my)) return false
		val i = p.cards.indices.firstOrNull { layout.card(it, scroll).contains(mx, my) } ?: return false
		return clickCard(i, button)
	}
}
