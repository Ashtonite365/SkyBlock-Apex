// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config.styled

import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.style.Style

object StyledSettingsLayout {
	const val HEADER = 24
	const val DOCK = 28
	const val DOCK_WIDE = 120
	const val DOCK_MS = 130L
	const val TAB_H = 18
	const val ROW = 22
	const val CARD_HEAD = 16

	const val MODULE_H = 18
	const val MODULE_GAP = 4
	const val PAD = 8

	fun frame(sw: Int, sh: Int): MenuBounds {
		val w = minOf(sw - 20, 500); val h = minOf(sh - 20, 320)
		return MenuBounds((sw - w) / 2, (sh - h) / 2, w, h)
	}

	const val DOCK_ITEM = 22
	const val DOCK_PAD = 4

	fun dockBox(f: dev.ashtonite365.apex.features.menus.MenuBounds): IntArray {
		val y = f.y + HEADER
		return intArrayOf(f.x + 6, y, (f.y + f.h - 8 - y).coerceAtLeast(DOCK_ITEM + 2 * DOCK_PAD))
	}

	fun dockMaxScroll(count: Int, h: Int) = (count * DOCK_ITEM - 2 + 2 * DOCK_PAD - h).coerceAtLeast(0)

	fun dockKeepVisible(scroll: Int, sel: Int?, count: Int, h: Int): Int {
		var s = scroll.coerceIn(0, dockMaxScroll(count, h))
		if (sel != null) {
			val top = sel * DOCK_ITEM; val bottom = top + 20
			val view = h - 2 * DOCK_PAD
			if (top < s) s = top
			if (bottom > s + view) s = bottom - view
		}
		return s.coerceIn(0, dockMaxScroll(count, h))
	}

	fun dockWidth(expanding: Boolean, ms: Long): Int {
		val t = (ms.toDouble() / DOCK_MS).coerceIn(0.0, 1.0)
		val f = if (expanding) t else 1 - t
		return DOCK + ((DOCK_WIDE - DOCK) * f).toInt()
	}

	fun content(f: MenuBounds, style: Style, pageChips: Boolean): MenuBounds {
		val chips = if (pageChips) TAB_H + 2 else 0
		return when (style) {
			Style.SKYGLASS -> MenuBounds(f.x + DOCK + PAD + 4, f.y + HEADER + chips, f.w - DOCK - 2 * PAD - 4, f.h - HEADER - chips - PAD)
			Style.FORGE -> MenuBounds(f.x + PAD, f.y + HEADER + TAB_H + 4 + chips, f.w - 2 * PAD, f.h - HEADER - TAB_H - 4 - chips - PAD)
		}
	}

	fun inRect(r: IntArray, mx: Double, my: Double) = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]

	fun icon(label: String) = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

	fun matches(query: String, vararg texts: String): Boolean {
		val words = query.lowercase().split(' ').filter { it.isNotBlank() }
		if (words.isEmpty()) return true
		val hay = texts.joinToString(" ").lowercase()
		return words.all { it in hay }
	}

	object Strip {
		const val GAP = 3
		const val STEP = 24
		fun lefts(widths: List<Int>): List<Int> { var x = 0; return widths.map { w -> x.also { x += w + GAP } } }
		fun total(widths: List<Int>) = if (widths.isEmpty()) 0 else widths.sum() + GAP * (widths.size - 1)
		fun maxScroll(total: Int, view: Int) = maxOf(0, total - view)
		fun clamp(scroll: Int, total: Int, view: Int) = scroll.coerceIn(0, maxScroll(total, view))

		fun wheel(scroll: Int, horizontal: Double, vertical: Double, total: Int, view: Int): Int {
			val amount = if (horizontal != 0.0) horizontal else vertical
			return clamp(scroll - (amount * STEP).toInt(), total, view)
		}

		fun ensureVisible(scroll: Int, left: Int, w: Int, total: Int, view: Int): Int = clamp(when {
			left < scroll -> left
			left + w > scroll + view -> left + w - view
			else -> scroll
		}, total, view)

		fun visible(x: Int, w: Int, vx: Int, view: Int): Pair<Int, Int>? {
			val a = maxOf(x, vx); val b = minOf(x + w, vx + view)
			return if (b > a) a to (b - a) else null
		}
		fun moreLeft(scroll: Int) = scroll > 0
		fun moreRight(scroll: Int, total: Int, view: Int) = scroll < maxScroll(total, view)
	}
}
