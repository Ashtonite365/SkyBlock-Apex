// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.TextInputFocus
import dev.ashtonite365.apex.util.UserCloseHook

open class MenuBrowseScreen(
	title: String,
	val view: MenuView,
	val defaultSize: Pair<Int, Int> = 520 to 290,
	val customisation: () -> MenuCustomisation = { MenuCustomisation() },
) : Screen(Component.literal(title)), TextInputFocus, UserCloseHook {

	override val textInputFocused get() = view.textFocused

	override fun interceptUserClose() = view.interceptClose()

	override fun init() {
		super.init()
		val wanted = customisation()
		val (fallbackW, fallbackH) = defaultSize
		val area = MenuBounds.centred(width, height, wanted.width ?: fallbackW, wanted.height ?: fallbackH, wanted.scalePct)
		view.layout(area.x, area.y, area.w, area.h)
	}

	override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(context, mouseX, mouseY, partialTick)
		view.render(context, mouseX, mouseY, partialTick)
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean =
		view.mouseClicked(click, doubled) || super.mouseClicked(click, doubled)

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean =
		view.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)

	override fun keyPressed(input: KeyEvent): Boolean = view.keyPressed(input) || super.keyPressed(input)

	override fun charTyped(input: CharacterEvent): Boolean = view.charTyped(input) || super.charTyped(input)

	override fun isPauseScreen(): Boolean = false
}
