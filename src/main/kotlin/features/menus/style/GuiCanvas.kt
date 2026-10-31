// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class GuiCanvas(val ctx: GuiGraphicsExtractor, val font: Font) : StyleCanvas {
	override fun fill(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int) { if (x1 > x0 && y1 > y0) ctx.fill(x0, y0, x1, y1, argb) }
	override fun text(s: String, x: Int, y: Int, argb: Int, shadow: Boolean) { ctx.text(font, s, x, y, argb, shadow || MenuStyle.palette.textShadow) }
	override fun frost(x: Int, y: Int, w: Int, h: Int, r: Int, alpha: Double) = Frost.draw(ctx, x, y, w, h, r, alpha * Frost.fade)
	override fun layer() = ctx.nextStratum()
	override fun textWidth(s: String) = font.width(s)
}
