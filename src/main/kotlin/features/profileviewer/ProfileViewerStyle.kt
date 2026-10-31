// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.profileviewer

import dev.ashtonite365.apex.features.menus.style.GuiCanvas
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.Press
import dev.ashtonite365.apex.features.menus.style.Shapes
import dev.ashtonite365.apex.features.menus.style.StyleWidgets
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.events.GuiEventListener

object ProfileViewerStyle {

	fun well(): Int = MenuStyle.palette.let { p -> if (p.style == dev.ashtonite365.apex.features.menus.style.Style.SKYGLASS) 0x4D0E141C else if (MenuStyle.state.dark) p.well or (0xF0 shl 24) else 0xF02A2018.toInt() }

	fun panel(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		val p = MenuStyle.palette; val c = GuiCanvas(ctx, font)
		Shapes.round(c, x, y + 3, w, h, p.cardRadius, p.shadow)
		if (p.blurWorld) c.frost(x, y, w, h, p.cardRadius)
		Shapes.roundFrame(c, x, y, w, h, p.cardRadius, p.window, p.border, p.style == dev.ashtonite365.apex.features.menus.style.Style.SKYGLASS)
		Shapes.round(c, x + 3, y + 3, w - 6, h - 6, maxOf(1, p.cardRadius - 1), well())
		dev.ashtonite365.apex.features.menus.style.ForgeMetal.plate(c, p, x, y, w, h)
	}

	fun grid(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, cols: Int, rows: Int, slot: Int) {
		val p = MenuStyle.palette; val c = GuiCanvas(ctx, font)
		for (r in 0 until rows) for (col in 0 until cols)
			Shapes.roundFrame(c, x + col * slot, y + r * slot, slot, slot, minOf(2, p.controlRadius), 0x40FFFFFF, 0x50FFFFFF)
	}

	fun buttons(ctx: GuiGraphicsExtractor, font: Font, children: List<GuiEventListener>, mx: Int, my: Int) {
		val p = MenuStyle.palette; val c = GuiCanvas(ctx, font)
		ctx.nextStratum()
		for (b in children) if (b is Button && b.visible) {
			val state = when {
				!b.active -> Press.ACTIVE
				b.isHovered -> Press.HOVER
				else -> Press.NORMAL
			}
			StyleWidgets.button(c, p, b.x, b.y, b.width, b.height, font.plainSubstrByWidth(b.message.string, b.width - 4), state)
		}
	}
}
