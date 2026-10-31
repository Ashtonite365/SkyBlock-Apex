package dev.ashtonite365.apex.test.features.auction

import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.ah.AhMenu
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AhRowClickNextViewTest {
	private class V : MenuView {
		override var x = 0; override var y = 0; override var w = 0; override var h = 0
		override fun layout(x: Int, y: Int, w: Int, h: Int) {}
		override fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
		override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean) = true
		override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = true
		override fun keyPressed(input: net.minecraft.client.input.KeyEvent) = false
		override fun charTyped(input: net.minecraft.client.input.CharacterEvent) = false
	}

	@AfterEach fun clear() = MenuRegistry.clearForTests()

	@Test fun binAndBidViewsAttachTheApexAhMenu() {
		MenuRegistry.clearForTests()
		MenuRegistry.register(object : CustomMenu by AhMenu { override val enabled = true })
		for (t in listOf("BIN Auction View", "Auction View", "Auctions Browser")) {
			val r = MenuRegistry.resolve(t) { V() }
			assertTrue(r is MenuRegistry.Resolution.Attach, "$t -> ${MenuRegistry.describe(r)}")
			assertEquals(AhMenu.id, (r as MenuRegistry.Resolution.Attach).menu.id)
		}
	}
}
