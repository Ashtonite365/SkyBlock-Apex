package dev.ashtonite365.apex.test.features.menus.m2

import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuView

object M2 {
	class View : MenuView {
		override var x = 0; override var y = 0; override var w = 0; override var h = 0
		override fun layout(x: Int, y: Int, w: Int, h: Int) {}
		override fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
		override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean) = true
		override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = true
		override fun keyPressed(input: net.minecraft.client.input.KeyEvent) = false
		override fun charTyped(input: net.minecraft.client.input.CharacterEvent) = false
	}
	fun reg(menu: CustomMenu, on: Boolean) {
		MenuRegistry.clearForTests(); MenuRegistry.register(object : CustomMenu by menu { override val enabled = on })
	}
	fun attaches(title: String) = MenuRegistry.resolve(title) { View() } is MenuRegistry.Resolution.Attach
	fun vanilla(title: String) = MenuRegistry.resolve(title) { View() } is MenuRegistry.Resolution.Vanilla
}
