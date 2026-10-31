// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.support.ApexViewBase

abstract class SkillViewBase<M : Any>(screen: AbstractContainerScreen<*>) : ApexViewBase<M>(screen) {
	protected fun header(ctx: GuiGraphicsExtractor, mx: Int, my: Int, title: String) {
		ctx.text(font, "§6§l$title", x + 6, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

	}

	protected fun group(ctx: GuiGraphicsExtractor, gx: Int, gy: Int, gw: Int, gh: Int, label: String): Int {
		MenuTheme.frame(ctx, gx, gy + 4, gx + gw, gy + gh, 0x40000000)
		val lw = font.width(label) + 6
		ctx.fill(gx + 5, gy, gx + 5 + lw, gy + 9, 0xFF202028.toInt())
		ctx.text(font, "§e$label", gx + 8, gy + 1, MenuTheme.WHITE)
		return gy + 12
	}

	protected fun tile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, tw: Int, th: Int,
	                   slot: Int, label: String, sub: String?, progress: Progress?, clickable: Boolean = true) {

		if (clickable) button(ctx, mx, my, tx, ty, tw, th, "") { b -> clickSlot(slot, b) }
		else MenuTheme.frame(ctx, tx, ty, tx + tw, ty + th, 0x30000000)
		stacks[slot]?.let { ctx.item(it, tx + 3, ty + 3) }

		val r = MenuTheme.BarTile.rects(tx, ty, tw, th, tx + 22, sub != null, progress != null)

		MenuTheme.drawFitted(ctx, font, label, r.title.x, r.title.y, r.title.w)
		r.sub?.let { MenuTheme.drawFitted(ctx, font, "§7" + sub!!, it.x, it.y, it.w, MenuTheme.GREY) }
		if (progress != null) r.bar?.let { b ->
			MenuTheme.progressBar(ctx, font, b.x, b.y, b.w, b.h, progress.fraction, 1.0, progress.label(), palette = palette())
		}
	}

	protected fun barTileH(base: Int) = maxOf(base, MenuTheme.BarTile.FULL)

	protected fun navTop() = y + h - 18

	protected fun navRow(ctx: GuiGraphicsExtractor, mx: Int, my: Int, nav: Map<String, Int>) {
		val by = navTop()

		(nav["Previous Page"] ?: nav["Scroll Left"])?.let { s -> button(ctx, mx, my, x + 4, by, 50, 14, "< Prev") { clickSlot(s, it) } }
		(nav["Next Page"] ?: nav["Scroll Right"])?.let { s -> button(ctx, mx, my, x + w - 54, by, 50, 14, "Next >") { clickSlot(s, it) } }
		val mid = listOfNotNull(nav["Go Back"]?.let { "Back" to it }, nav["Re-Center"]?.let { "Re-Center" to it }, nav["Close"]?.let { "Close" to it })
		var bx = x + (w - mid.size * 54) / 2
		for ((l, s) in mid) { button(ctx, mx, my, bx, by, 50, 14, l) { clickSlot(s, it) }; bx += 54 }
	}
}
