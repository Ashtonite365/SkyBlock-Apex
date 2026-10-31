// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import net.minecraft.client.gui.GuiGraphicsExtractor

object InventoryWidgetStyle {

	data class Look(val line: Int, val fill: Int, val hi: Int, val lo: Int, val text: Int, val textDim: Int)

	fun look(state: StyleState, hovered: Boolean = false): Look? {
		if (InventoryTexture.pathFor(state) == null || state.style != Style.FORGE) return null
		val p = StylePalette.of(state)
		return Look(if (hovered) p.accent else p.buttonEdge, if (hovered) p.buttonHover else p.buttonFill, p.accent, p.shadow, p.text, p.textDim)
	}

	fun frameRects(x: Int, y: Int, w: Int, h: Int, l: Look): List<IntArray> {
		val r = x + w; val b = y + h
		return listOf(
			intArrayOf(x + 1, y, r - 1, y + 1, l.line), intArrayOf(x + 1, b - 1, r - 1, b, l.line),
			intArrayOf(x, y + 1, x + 1, b - 1, l.line), intArrayOf(r - 1, y + 1, r, b - 1, l.line),
			intArrayOf(x + 1, y + 1, r - 1, b - 1, l.fill),
			intArrayOf(x + 1, y + 1, r - 2, y + 2, l.hi), intArrayOf(x + 1, y + 2, x + 2, b - 2, l.hi),
			intArrayOf(x + 2, b - 2, r - 1, b - 1, l.lo), intArrayOf(r - 2, y + 2, r - 1, b - 2, l.lo),
		)
	}

	val VANILLA = Look(0xFF000000.toInt(), 0xFFC6C6C6.toInt(), 0xFFFFFFFF.toInt(), 0xFF555555.toInt(), -1, 0xFF808080.toInt())
	val VANILLA_HOVER = VANILLA.copy(fill = 0xFFD8D8D8.toInt())

	fun frameLook(state: StyleState, hovered: Boolean = false): Look? =
		look(state, hovered) ?: if (!state.customMenus) (if (hovered) VANILLA_HOVER else VANILLA) else null

	@JvmStatic
	fun draw(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, hovered: Boolean): Boolean {
		val l = frameLook(MenuStyle.state, hovered) ?: return false
		for (f in frameRects(x, y, w, h, l)) g.fill(f[0], f[1], f[2], f[3], f[4])
		return true
	}

	fun textColourFor(state: StyleState, original: Int): Int {
		val l = look(state) ?: return original
		return when (original) { -1 -> l.text; 0xFF808080.toInt() -> l.textDim; else -> original }
	}

	@JvmStatic
	fun textColour(original: Int): Int = textColourFor(MenuStyle.state, original)
}
