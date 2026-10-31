// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.StyleChoice
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

object VanillaStyleDropdown {
	const val W = 64
	const val H = 12
	const val ITEM_H = 12
	const val GAP = 2

	fun place(left: Int, top: Int, imageWidth: Int): Pair<Int, Int> = (left + imageWidth - W) to maxOf(1, top - H - GAP)

	fun itemY(y: Int, i: Int) = y + H + 1 + i * ITEM_H

	fun listRect(x: Int, y: Int, sw: Int = Int.MAX_VALUE, sh: Int = Int.MAX_VALUE): IntArray {
		val h = StyleChoice.entries.size * ITEM_H + 2
		val lx = if (sw == Int.MAX_VALUE) x else x.coerceIn(0, maxOf(0, sw - W))
		val below = y + H
		val ly = if (sh == Int.MAX_VALUE || below + h <= sh) below else maxOf(0, y - h)
		return intArrayOf(lx, ly, W, h)
	}

	fun rowY(rect: IntArray, i: Int) = rect[1] + 1 + i * ITEM_H

	fun click(x: Int, y: Int, open: Boolean, mx: Double, my: Double, sw: Int = Int.MAX_VALUE, sh: Int = Int.MAX_VALUE): Pair<Boolean, Boolean> {
		if (open) {
			val r = listRect(x, y, sw, sh)
			StyleChoice.entries.forEachIndexed { i, c ->
				if (mx >= r[0] && mx < r[0] + W && my >= rowY(r, i) && my < rowY(r, i) + ITEM_H) { MenuStyle.choose(c); return true to false }
			}
			return true to false
		}
		val onHead = mx >= x && mx < x + W && my >= y && my < y + H
		return onHead to onHead
	}

	enum class Renderer { STYLE_BAR, PLAIN }

	fun rendererFor(state: dev.ashtonite365.apex.features.menus.style.StyleState): Renderer =
		if (state.customMenus) Renderer.STYLE_BAR else Renderer.PLAIN

	data class ListLook(val bg: Int, val border: Int, val hover: Int, val text: Int, val accent: Int)
	val PLAIN_LIST = ListLook(0xFF100010.toInt(), 0xFF5A5A78.toInt(), 0x40FFFFFF, 0xFFFFFFFF.toInt(), 0xFFFFC94A.toInt())
	fun listLook(state: dev.ashtonite365.apex.features.menus.style.StyleState): ListLook =
		paletteFor(state)?.let { listLook(it) } ?: PLAIN_LIST
	fun listLook(p: dev.ashtonite365.apex.features.menus.style.StylePalette) = ListLook(p.raised or (0xFF shl 24), p.border, p.buttonHover, p.text, p.accent)

	fun paletteFor(state: dev.ashtonite365.apex.features.menus.style.StyleState) =
		if (rendererFor(state) == Renderer.STYLE_BAR) dev.ashtonite365.apex.features.menus.style.StylePalette.of(state) else null

	fun paintHead(c: dev.ashtonite365.apex.features.menus.style.StyleCanvas, p: dev.ashtonite365.apex.features.menus.style.StylePalette?,
		label: String, x: Int, y: Int, w: Int, h: Int, hover: Boolean, open: Boolean) {
		val ty = y + (h - 8) / 2
		if (p != null) {
			val look = dev.ashtonite365.apex.features.menus.style.StyleWidgets.buttonLook(p, dev.ashtonite365.apex.features.menus.style.StyleWidgets.press(hover, false, open))
			dev.ashtonite365.apex.features.menus.style.Shapes.pill(c, x, y, w, h, look.fill, look.border)
			c.text(label, x + 6, ty, look.text); c.text("▾", x + w - 10, ty, look.text)
			return
		}
		c.fill(x, y, x + w, y + h, if (hover) 0xFFFFFFFF.toInt() else 0xFF1E1E1E.toInt())
		c.fill(x + 1, y + 1, x + w - 1, y + h - 1, if (hover) 0xFFDCDCDC.toInt() else 0xFFC6C6C6.toInt())
		c.text(label, x + 4, ty, 0xFF404040.toInt()); c.text("▾", x + w - 9, ty, 0xFF404040.toInt())
	}

	fun paintRows(c: dev.ashtonite365.apex.features.menus.style.StyleCanvas, p: dev.ashtonite365.apex.features.menus.style.StylePalette?,
		r: IntArray, labels: List<String>, current: Int, mx: Int, my: Int, itemH: Int = ITEM_H) {
		c.layer()
		val (x, y, w, h) = r.toList()
		val look = p?.let { listLook(it) } ?: PLAIN_LIST
		if (p != null) dev.ashtonite365.apex.features.menus.style.Shapes.roundFrame(c, x, y, w, h, p.controlRadius, look.bg, look.border)
		else { c.fill(x, y, x + w, y + h, look.border); c.fill(x + 1, y + 1, x + w - 1, y + h - 1, look.bg) }
		labels.forEachIndexed { i, label ->
			val iy = y + 1 + i * itemH
			if (mx >= x && mx < x + w && my >= iy && my < iy + itemH) c.fill(x + 1, iy, x + w - 1, iy + itemH, look.hover)
			if (i == current) c.fill(x + 2, iy + 2, x + 4, iy + itemH - 2, look.accent)
			c.text(label, x + 7, iy + (itemH - 8) / 2, look.text)
		}
	}

	fun paintList(c: dev.ashtonite365.apex.features.menus.style.StyleCanvas, r: IntArray, state: dev.ashtonite365.apex.features.menus.style.StyleState, mx: Int, my: Int) =
		paintRows(c, paletteFor(state), r, StyleChoice.entries.map { it.label }, StyleChoice.entries.indexOf(StyleChoice.of(state)), mx, my)

	fun isLeftPress(button: Int) = dev.ashtonite365.apex.features.menus.support.MenuButtons.isLeft(button)

	fun wanted(isInventory: Boolean, isChest: Boolean, inSkyBlock: Boolean, customMenus: Boolean) =
		isInventory || (isChest && inSkyBlock && !customMenus)

	class Dropdown(private val window: () -> IntArray?, private val screenSize: () -> Pair<Int, Int> = { Int.MAX_VALUE to Int.MAX_VALUE }) : AbstractWidget(0, 0, W, H, Component.literal("Menu style")) {
		var open = false
		fun listRect() = screenSize().let { (sw, sh) -> listRect(x, y, sw, sh) }
		fun sync() { window()?.let { val (x, y) = place(it[0], it[1], it[2]); setX(x); setY(y) } }

		override fun isMouseOver(mx: Double, my: Double): Boolean =
			visible && ((mx >= x && mx < x + W && my >= y && my < y + H) ||
				(open && listRect().let { r -> mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3] }))

		fun claim(mx: Double, my: Double, button: Int): Boolean {
			if (!visible) return false

			val left = isLeftPress(button)
			if (!open && !left) return false
			val (sw, sh) = screenSize()
			if (open && !left) { open = false; return true }
			val (ours, nowOpen) = click(x, y, open, mx, my, sw, sh)
			open = nowOpen
			return ours
		}

		override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
			if (!visible) return false
			val (sw, sh) = screenSize()
			val (ours, nowOpen) = click(x, y, open, event.x(), event.y(), sw, sh)
			open = nowOpen
			return ours
		}

		override fun extractWidgetRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
			sync()
			val font = net.minecraft.client.Minecraft.getInstance().font
			val st = MenuStyle.state
			val hov = mx >= x && mx < x + W && my >= y && my < y + H

			paintHead(dev.ashtonite365.apex.features.menus.style.GuiCanvas(g, font), paletteFor(st),
				font.plainSubstrByWidth(StyleChoice.of(st).label, W - 16), x, y, W, H, hov, open)

		}

		fun drawLate(g: GuiGraphicsExtractor, mx: Int, my: Int) {
			if (!visible || !open) return
			val font = net.minecraft.client.Minecraft.getInstance().font
			paintList(dev.ashtonite365.apex.features.menus.style.GuiCanvas(g, font), listRect(), MenuStyle.state, mx, my)
		}

		override fun updateWidgetNarration(out: NarrationElementOutput) =
			out.add(NarratedElementType.TITLE, Component.literal("Menu style: " + StyleChoice.of(MenuStyle.state).label))
	}

	fun install(screen: Screen) {
		if (screen !is AbstractContainerScreen<*>) return
		if (!wanted(screen is InventoryScreen, screen is ContainerScreen, dev.ashtonite365.apex.util.SkyBlockSession.inSkyBlock, MenuStyle.state.customMenus)) return
		val acc = screen as dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
		val d = Dropdown({ intArrayOf(acc.x_Apex, acc.y_Apex, acc.backgroundWidth_Apex) }, { screen.width to screen.height })
		d.sync()
		Screens.getWidgets(screen).add(d)
		DropdownLayer.attach(screen, d)
	}
}
