// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.gui.config.AllConfigsGui
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object ApexInventoryButton {
	const val W = 20
	const val H = 18
	const val GAP = 2
	val LOGO: Identifier = Identifier.fromNamespaceAndPath("apex", "textures/gui/apex_logo.png")

	val LOGO_HOVER: Identifier = Identifier.fromNamespaceAndPath("apex", "textures/gui/apex_logo_hover.png")

	const val TEX = 32

	const val ICON = 14

	fun iconRect(x: Int, y: Int) = intArrayOf(x + (W - ICON) / 2, y + (H - ICON) / 2, x + (W - ICON) / 2 + ICON, y + (H - ICON) / 2 + ICON)

	fun frameRects(x: Int, y: Int, hovered: Boolean): List<IntArray> {
		val line = if (hovered) 0xFFFFFFFF.toInt() else 0xFF1E1E1E.toInt()
		val fill = if (hovered) 0xFFDCDCDC.toInt() else 0xFFC6C6C6.toInt()
		val hi = 0xFFFFFFFF.toInt(); val lo = if (hovered) 0xFF6E6E6E.toInt() else 0xFF555555.toInt()
		val r = x + W; val b = y + H
		return listOf(
			intArrayOf(x + 1, y, r - 1, y + 1, line), intArrayOf(x + 1, b - 1, r - 1, b, line),
			intArrayOf(x, y + 1, x + 1, b - 1, line), intArrayOf(r - 1, y + 1, r, b - 1, line),
			intArrayOf(x + 1, y + 1, r - 1, b - 1, fill),
			intArrayOf(x + 1, y + 1, r - 2, y + 2, hi), intArrayOf(x + 1, y + 2, x + 2, b - 2, hi),
			intArrayOf(x + 2, b - 2, r - 1, b - 1, lo), intArrayOf(r - 2, y + 2, r - 1, b - 2, lo),
		)
	}

	fun sprite(hovered: Boolean): Identifier = if (hovered) LOGO_HOVER else LOGO

	const val WINDOW_W = 176
	const val FRAME = 7

	const val ROW_Y = 61

	fun dock(left: Int, top: Int): Pair<Int, Int> = (left + WINDOW_W - FRAME - W) to (top + ROW_Y)

	fun colours(state: dev.ashtonite365.apex.features.menus.style.StyleState, hovered: Boolean): IntArray? {
		if (dev.ashtonite365.apex.features.menus.style.InventoryTexture.pathFor(state) == null) return null
		val p = dev.ashtonite365.apex.features.menus.style.StylePalette.of(state)
		return intArrayOf(if (hovered) p.borderHover else p.buttonEdge, if (hovered) p.buttonHover else p.buttonFill, p.rimHighlight, p.shadow)
	}

	fun styledFrameRects(x: Int, y: Int, hovered: Boolean, state: dev.ashtonite365.apex.features.menus.style.StyleState): List<IntArray> {
		dev.ashtonite365.apex.features.menus.style.InventoryWidgetStyle.look(state, hovered)?.let {
			return dev.ashtonite365.apex.features.menus.style.InventoryWidgetStyle.frameRects(x, y, W, H, it)
		}
		val c = colours(state, hovered) ?: return frameRects(x, y, hovered)
		val base = frameRects(x, y, hovered)
		return base.mapIndexed { i, r -> r.copyOf().also { it[4] = when (i) { in 0..3 -> c[0]; 4 -> c[1]; 5, 6 -> c[2]; else -> c[3] } } }
	}

	fun place(rx: Int, ry: Int): Pair<Int, Int> = (rx + W + GAP) to ry

	fun recipeButton(widgets: List<AbstractWidget>): AbstractWidget? = widgets.firstOrNull { it is ImageButton }

	var open: () -> Unit = { AllConfigsGui.showAllGuis() }

	class Button(private val window: () -> Pair<Int, Int>?) : AbstractButton(0, 0, W, H, Component.literal("SkyBlock Apex")) {
		init { setTooltip(Tooltip.create(Component.literal("SkyBlock Apex"))); sync() }
		fun sync() {
			val (l, tp) = window() ?: return
			val (x, y) = dock(l, tp); setX(x); setY(y)
			visible = MenuStyle.state.inventoryButton
			active = visible
		}
		override fun onPress(input: InputWithModifiers) { if (MenuStyle.state.inventoryButton) open() }
		override fun extractContents(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {

			for (f in styledFrameRects(x, y, isHoveredOrFocused, MenuStyle.state)) g.fill(f[0], f[1], f[2], f[3], f[4])
			val r = iconRect(x, y)
			g.blit(sprite(isHoveredOrFocused), r[0], r[1], r[2], r[3], 0f, 1f, 0f, 1f)
		}
		override fun updateWidgetNarration(out: NarrationElementOutput) = defaultButtonNarrationText(out)
	}

	fun install(screen: Screen) {
		if (screen !is InventoryScreen) return
		val acc = screen as dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
		val b = Button { acc.x_Apex to acc.y_Apex }
		Screens.getWidgets(screen).add(b)
		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeExtract(screen).register { _, _, _, _, _ -> b.sync() }
	}
}
