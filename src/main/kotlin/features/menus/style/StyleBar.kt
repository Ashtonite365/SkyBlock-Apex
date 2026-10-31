// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

class StyleBar {
	var open = false; private set

	data class Geometry(val dropX: Int, val dropY: Int, val dropW: Int, val switchX: Int, val y: Int, val h: Int, val listTop: Int = y + h + 2) {
		fun inDrop(mx: Double, my: Double) = mx >= dropX && mx < dropX + dropW && my >= y && my < y + h
		fun inSwitch(mx: Double, my: Double) = switchX >= 0 && mx >= switchX && mx < switchX + 22 && my >= y && my < y + h

		fun itemY(i: Int) = listTop + i * ITEM_H
		fun inItem(i: Int, mx: Double, my: Double) = mx >= dropX && mx < dropX + dropW && my >= itemY(i) && my < itemY(i) + ITEM_H
	}

	companion object {
		const val H = 14
		const val DROP_W = 70

		const val DROP_W_FORGE = 76
		fun dropW(s: StyleState) = if (s.style == Style.FORGE && s.customMenus) DROP_W_FORGE else DROP_W
		const val ITEM_H = 14

		const val SWITCH_W = 22
		const val ICON_SLOT = 18

		fun switchTooltip(g: Geometry, s: StyleState, mx: Double, my: Double): String? =
			if (g.switchX >= 0 && mx >= g.switchX - ICON_SLOT + 4 && mx < g.switchX + SWITCH_W && my >= g.y && my < g.y + g.h)
				"Forge: " + (if (s.dark) "Dark" else "Light") + " (click to switch)" else null

		fun iconX(g: Geometry) = g.switchX - ICON_SLOT + 5

		fun geometry(right: Int, y: Int, s: StyleState, screenH: Int = Int.MAX_VALUE): Geometry {
			val sw = if (s.style == Style.FORGE && s.customMenus) SWITCH_W + ICON_SLOT else 0
			val dropX = right - dropW(s) - sw
			return Geometry(dropX, y, dropW(s), if (sw > 0) right - SWITCH_W else -1, y, H, listTop(y, screenH))
		}

		fun listTop(y: Int, screenH: Int): Int {
			val listH = StyleChoice.entries.size * ITEM_H + 2
			val below = y + H + 2
			return if (screenH == Int.MAX_VALUE || below - 1 + listH <= screenH) below else maxOf(1, y - listH)
		}
	}

	fun width(s: StyleState) = dropW(s) + if (s.style == Style.FORGE && s.customMenus) SWITCH_W + ICON_SLOT else 0

	fun listRect(g: Geometry) = intArrayOf(g.dropX, g.itemY(0) - 1, g.dropW, StyleChoice.entries.size * ITEM_H + 2)

	fun click(g: Geometry, mx: Double, my: Double): Boolean {
		if (open) {
			StyleChoice.entries.forEachIndexed { i, st -> if (g.inItem(i, mx, my)) { MenuStyle.choose(st); open = false; return true } }
			open = false
			return true
		}
		if (g.inDrop(mx, my)) { open = true; return true }
		if (g.inSwitch(mx, my)) { MenuStyle.toggleForgeMode(); return true }
		return false
	}

	fun close() { open = false }

	fun draw(c: StyleCanvas, p: StylePalette, s: StyleState, g: Geometry, mx: Int, my: Int, held: Boolean) {
		drawButton(c, p, s, g, mx, my, held); drawList(c, p, s, g, mx, my)
	}

	fun drawButton(c: StyleCanvas, p: StylePalette, s: StyleState, g: Geometry, mx: Int, my: Int, held: Boolean) {
		val hov = g.inDrop(mx.toDouble(), my.toDouble())
		val look = StyleWidgets.buttonLook(p, StyleWidgets.press(hov, held, open))
		Shapes.pill(c, g.dropX, g.y, g.dropW, g.h, look.fill, look.border)
		c.text(StyleChoice.of(s).label, g.dropX + 7, g.y + 3, look.text)
		c.text("▾", g.dropX + g.dropW - 11, g.y + 3, look.text)
		if (g.switchX >= 0) {
			c.text(if (s.dark) "☾" else "☀", iconX(g), g.y + 3, p.textDim)
			StyleWidgets.toggle(c, p, g.switchX, g.y + 2, if (s.dark) 1.0 else 0.0)
		}
	}

	fun drawList(c: StyleCanvas, p: StylePalette, s: StyleState, g: Geometry, mx: Int, my: Int) {
		if (!open) switchTooltip(g, s, mx.toDouble(), my.toDouble())?.let { tip ->
			c.layer()
			val tw = c.textWidth(tip) + 8; val tx = minOf(mx + 6, g.switchX + SWITCH_W - tw).coerceAtLeast(0); val ty = g.y + g.h + 3
			Shapes.roundFrame(c, tx, ty, tw, 12, p.controlRadius, p.raised or (0xF0 shl 24), p.border)
			c.text(tip, tx + 4, ty + 2, p.text)
		}
		if (open) {
			val n = StyleChoice.entries.size

			c.layer(); Shapes.roundFrame(c, g.dropX, g.itemY(0) - 1, g.dropW, n * ITEM_H + 2, p.controlRadius, p.raised or (0xFF shl 24), p.border)
			StyleChoice.entries.forEachIndexed { i, st ->
				val ih = g.inItem(i, mx.toDouble(), my.toDouble())
				if (ih) c.fill(g.dropX + 2, g.itemY(i), g.dropX + g.dropW - 2, g.itemY(i) + ITEM_H, p.buttonHover)
				if (st == StyleChoice.of(s)) c.fill(g.dropX + 2, g.itemY(i) + 2, g.dropX + 4, g.itemY(i) + ITEM_H - 2, p.accent)
				c.text(st.label, g.dropX + 8, g.itemY(i) + 3, p.text)
			}
		}
	}
}
