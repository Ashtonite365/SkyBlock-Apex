package dev.ashtonite365.apex.test.features.menus.garden

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.garden.SamButton
import dev.ashtonite365.apex.features.menus.garden.SamMenu
import dev.ashtonite365.apex.features.menus.garden.SamParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SamMenuTest {
	private class View : MenuView {
		override var x = 0; override var y = 0; override var w = 0; override var h = 0
		override fun layout(x: Int, y: Int, w: Int, h: Int) {}
		override fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {}
		override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean) = true
		override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = true
		override fun keyPressed(input: net.minecraft.client.input.KeyEvent) = false
		override fun charTyped(input: net.minecraft.client.input.CharacterEvent) = false
	}
	private fun reg(on: Boolean) { MenuRegistry.clearForTests(); MenuRegistry.register(object : CustomMenu by SamMenu { override val enabled = on }) }

	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test
	fun titleMatches() {
		val d = RealDumpFixtures.load("sam")
		assertEquals("Sam the Garden Assistant", d.title)
		assertTrue(SamParser.TITLE.matches(MenuRegistry.strip(d.title)))
		reg(true)
		assertEquals("sam", MenuRegistry.match("§aSam the Garden Assistant")?.id)
		assertNull(MenuRegistry.match("Garden"))
		assertNull(SamParser.parse("Jerry", d.slots))
	}

	@Test
	fun slotMappings() {
		val d = RealDumpFixtures.load("sam")
		assertEquals("Warp to Garden", d.slots.first { it.slot == 13 }.name)
		assertEquals("Close", d.slots.first { it.slot == 31 }.name)
		assertEquals("Move Sam", d.slots.first { it.slot == 35 }.name)
		val m = SamParser.parse(d.title, d.slots)
		assertNotNull(m)
		assertEquals(mapOf(SamButton.WARP to 13, SamButton.CLOSE to 31, SamButton.MOVE to 35), m!!.slots)

		assertNull(SamParser.parse(d.title, d.slots.filter { it.slot != 13 }))
	}

	@Test
	fun vanillaToggle() {
		reg(false)
		assertTrue(MenuRegistry.resolve("Sam the Garden Assistant") { View() } is MenuRegistry.Resolution.Vanilla)
	}

	@Test
	fun rebindAfterReopen() {
		reg(true)
		val first = MenuRegistry.resolve("Sam the Garden Assistant") { View() } as MenuRegistry.Resolution.Attach
		val second = MenuRegistry.resolve("Sam the Garden Assistant") { View() } as MenuRegistry.Resolution.Attach
		assertSame(first.menu, second.menu)
		assertNotSame(first.view, second.view)
		val d = RealDumpFixtures.load("sam")
		assertEquals(SamParser.parse(d.title, d.slots), SamParser.parse(d.title, d.slots.toList()))
	}
}
