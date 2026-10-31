// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.ContainerGate
import dev.ashtonite365.apex.features.menus.ContainerSettle
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.features.menus.MenuCustomisation
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView

class MenuFrameworkTest {
	private class Fake(override val id: String, pattern: String) : CustomMenu {
		override val displayName = id
		override val title = Regex(pattern)
		override val settings: MenuSettings get() = error("not used by match()")
	}

	@AfterEach
	fun cleanup() { MenuRegistry.clearForTests(); ApexClickDelay.resetForTests() }

	@Test
	fun titleRegexMatchesStrippedTitles() {
		MenuRegistry.clearForTests()
		MenuRegistry.register(Fake("ah", "^Auction House$|^Auctions Browser"))
		MenuRegistry.register(Fake("bank", "^(?:Personal |Co-op )?Bank Account$|^Bank (?:Deposit|Withdrawal)$"))
		Assertions.assertEquals("ah", MenuRegistry.match("§6Auction House")?.id)
		Assertions.assertEquals("ah", MenuRegistry.match("  Auctions Browser ")?.id)
		Assertions.assertEquals("bank", MenuRegistry.match("§8Co-op Bank Account")?.id)
		Assertions.assertEquals("bank", MenuRegistry.match("Bank Deposit")?.id)
		Assertions.assertNull(MenuRegistry.match("Bazaar ➜ Farming"))
		Assertions.assertThrows(IllegalArgumentException::class.java) { MenuRegistry.register(Fake("ah", "x")) }

		MenuRegistry.register(Fake("museum", "Auction"))
		Assertions.assertEquals("ah", MenuRegistry.match("Auction House")?.id)
	}

	@Test
	fun boundsScaleClampAndCentre() {
		Assertions.assertEquals(MenuBounds(140, 115, 520, 290), MenuBounds.centred(800, 520, 520, 290))

		Assertions.assertEquals(MenuBounds(6, 6, 388, 288), MenuBounds.centred(400, 300, 520, 290))
		Assertions.assertEquals(MenuBounds(270, 187, 260, 145), MenuBounds.centred(800, 520, 520, 290, scalePct = 50))
		Assertions.assertEquals(MenuBounds(6, 6, 788, 508), MenuBounds.centred(800, 520, 520, 290, scalePct = 200))
	}

	@Test
	fun themeStatesColoursAndAccent() {
		val S = MenuTheme.State.entries
		Assertions.assertEquals(S[4], MenuTheme.state(hover = true, active = true, enabled = false, pressed = true))
		Assertions.assertEquals(S[2], MenuTheme.state(hover = true, active = true, enabled = true, pressed = true))
		Assertions.assertEquals(S[3], MenuTheme.state(hover = true, active = true, enabled = true, pressed = false))
		Assertions.assertEquals(S[1], MenuTheme.state(hover = true, active = false, enabled = true, pressed = false))
		Assertions.assertEquals(S[0], MenuTheme.state(hover = false, active = false, enabled = true, pressed = true))

		Assertions.assertEquals(Triple(MenuTheme.BORDER_ACTIVE, MenuTheme.FILL_ACTIVE, MenuTheme.GOLD), MenuTheme.buttonColours(MenuTheme.State.ACTIVE))
		Assertions.assertEquals(Triple(MenuTheme.BORDER, MenuTheme.FILL, MenuTheme.WHITE), MenuTheme.buttonColours(MenuTheme.State.NORMAL))
		val teal = MenuTheme.Palette.DEFAULT.withAccent(0x00AAAA)
		Assertions.assertEquals(0xFF00AAAA.toInt(), teal.accent)
		Assertions.assertEquals(Triple(0xFF00AAAA.toInt(), MenuTheme.FILL_PRESSED, 0xFF00AAAA.toInt()), MenuTheme.buttonColours(MenuTheme.State.PRESSED, teal))
		Assertions.assertTrue(MenuTheme.inside(5, 5, 5, 5, 1, 1)); Assertions.assertFalse(MenuTheme.inside(6, 5, 5, 5, 1, 1))
	}

	@Test
	fun fitEllipsises() {
		val m: (String) -> Int = { it.length * 6 }
		Assertions.assertEquals("Short", MenuTheme.fit("Short", 30, m))
		Assertions.assertEquals("Enchant…", MenuTheme.fit("Enchanted Quartz", 48, m))
		Assertions.assertEquals("…", MenuTheme.fit("abc", 0, m))
	}

	@Test
	fun customisationDefaults() {
		val c = MenuCustomisation()
		Assertions.assertNull(c.width); Assertions.assertEquals(100, c.scalePct)

		Assertions.assertEquals(dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.theme(), c.palette)
		val d = MenuCustomisation(panels = setOf("graphs"), accent = 0xFF112233.toInt())
		Assertions.assertTrue(d.shows("graphs")); Assertions.assertFalse(d.shows("orders"))
		Assertions.assertEquals(0xFF112233.toInt(), d.palette.accent)
	}

	@Test
	fun clickDelayInjectableAndClamped() {
		Assertions.assertEquals(100..200, ApexClickDelay.RANGE_MS)
		ApexClickDelay.random = { 999 }; Assertions.assertEquals(200L, ApexClickDelay.nextMs())
		ApexClickDelay.random = { -5 }; Assertions.assertEquals(100L, ApexClickDelay.nextMs())
		ApexClickDelay.random = { 117 }; Assertions.assertEquals(117L, ApexClickDelay.nextMs())
		ApexClickDelay.resetForTests()
		repeat(200) { Assertions.assertTrue(ApexClickDelay.nextMs() in 100L..200L) }
	}

	@Test
	fun gateWaitsForNewContainerAndSettle() {
		val g = ContainerGate(3)
		Assertions.assertFalse(g.isNew(3, 0))
		Assertions.assertTrue(g.isNew(4, 100))
		Assertions.assertTrue(g.stillPopulating(100 + ContainerGate.GRACE_MS - 1))
		Assertions.assertFalse(g.stillPopulating(100 + ContainerGate.GRACE_MS))
		g.done(4); Assertions.assertFalse(g.isNew(4, 2000))
		val s = ContainerSettle()
		Assertions.assertFalse(s.stable(4, "a")); Assertions.assertFalse(s.stable(4, "b")); Assertions.assertTrue(s.stable(4, "b"))
		Assertions.assertFalse(s.stable(5, "b"))
	}
}

class MenuAttachTest {

	private class Dummy(override val id: String, pattern: String, override val enabled: Boolean = true,
		override val selfHosted: Boolean = false, val make: () -> MenuView?) : CustomMenu {
		override val displayName = id
		override val title = Regex(pattern)
		override val settings: MenuSettings get() = error("not needed")
		override fun create(screen: net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) = make()
	}

	private class DummyView : MenuView {
		override var x = 0; override var y = 0; override var w = 0; override var h = 0
		var rendered = 0
		override fun layout(x: Int, y: Int, w: Int, h: Int) { this.x = x; this.y = y; this.w = w; this.h = h }
		override fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) { rendered++ }
		override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean) = true
		override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) = true
		override fun keyPressed(input: net.minecraft.client.input.KeyEvent) = false
		override fun charTyped(input: net.minecraft.client.input.CharacterEvent) = false
	}

	@AfterEach
	fun cleanup() = MenuRegistry.clearForTests()

	private fun reg(m: CustomMenu) { MenuRegistry.clearForTests(); MenuRegistry.register(m) }
	private fun resolve(t: String) = MenuRegistry.resolve(t) { (it as Dummy).make() }

	@Test
	fun dummyMenuAttachesWithItsView() {
		val v = DummyView()
		reg(Dummy("bank", "^Bank Account$") { v })
		val r = resolve("§6Bank Account")
		Assertions.assertTrue(r is MenuRegistry.Resolution.Attach)
		Assertions.assertSame(v, (r as MenuRegistry.Resolution.Attach).view)
		Assertions.assertEquals("bank", r.menu.id)
		Assertions.assertEquals("bank", MenuRegistry.describe(r))
		Assertions.assertEquals(MenuRegistry.Resolution.None, resolve("Auction House"))
	}

	@Test
	fun normalModeSelfHostedAndDenied() {
		reg(Dummy("bank", "^Bank Account$", enabled = false) { DummyView() })
		Assertions.assertTrue(resolve("Bank Account") is MenuRegistry.Resolution.Vanilla)
		reg(Dummy("bz", "^Bazaar") { error("self-hosted menus are never created by the registry") }.let { object : CustomMenu by it { override val selfHosted = true } })
		Assertions.assertTrue(resolve("Bazaar ➜ Farming") is MenuRegistry.Resolution.SelfHosted)

		reg(Dummy("greedy", ".*") { DummyView() })
		Assertions.assertEquals(MenuRegistry.Resolution.None, resolve("Chronomatron (Expert)"))
		Assertions.assertEquals(MenuRegistry.Resolution.None, resolve("Click in order!"))

		Assertions.assertFalse(MenuRegistry.denied("Experimentation Table"))
		Assertions.assertTrue(MenuRegistry.denied("Superpairs (Metaphysical)"))
		Assertions.assertTrue(MenuRegistry.denied("Experiment Over"))
		Assertions.assertTrue(MenuRegistry.denied("Experimentation Table RNG"))
		Assertions.assertFalse(MenuRegistry.denied("Bank Account"))
	}

	@Test
	fun parseFailureOrThrowFallsBackToVanilla() {
		reg(Dummy("ah", "^Auctions Browser") { null })
		val r = resolve("Auctions Browser")
		Assertions.assertTrue(r is MenuRegistry.Resolution.Fallback)
		Assertions.assertEquals("ah (fallback)", MenuRegistry.describe(r))
		reg(Dummy("ah", "^Auctions Browser") { throw IllegalStateException("no price lore") })
		val t = resolve("Auctions Browser") as MenuRegistry.Resolution.Fallback
		Assertions.assertTrue(t.reason.contains("IllegalStateException") && t.reason.contains("no price lore"))
		Assertions.assertTrue(t.error is IllegalStateException)
	}

	@Test
	fun sessionFailoverLogsOnceAndStopsCallingTheView() {
		val fails = mutableListOf<String>()
		val f = dev.ashtonite365.apex.features.menus.MenuFailover { r, _ -> fails += r }
		Assertions.assertEquals(5, f.guard("render", 0) { 5 })
		Assertions.assertFalse(f.failed)
		var calls = 0
		Assertions.assertEquals(-1, f.guard("render", -1) { calls++; throw RuntimeException("boom") })
		Assertions.assertTrue(f.failed)
		Assertions.assertEquals(-1, f.guard("render", -1) { calls++; 7 })
		f.fail("again")
		Assertions.assertEquals(1, calls)
		Assertions.assertEquals(listOf("render threw RuntimeException: boom"), fails)
	}
}

class MenuThemeWidgetsTest {
	@Test
	fun themeConstantSnapshot() {

		Assertions.assertEquals(listOf(0xF0141418.toInt(), 0xFF5A5A66.toInt(), 0xFF9A9AAA.toInt(), 0xFFFFAA00.toInt(), 0xFF202028.toInt(),
			0xFF3A3A50.toInt(), 0xFF101014.toInt(), 0xFF3A3020.toInt(), 0xFF18181C.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFAA00.toInt(),
			0xFFA0A0A0.toInt(), 0xFF606060.toInt(), 0x50FFAA00, 0xF0200000.toInt(), 0xFF909090.toInt()),
			listOf(MenuTheme.BG, MenuTheme.BORDER, MenuTheme.BORDER_HOVER, MenuTheme.BORDER_ACTIVE, MenuTheme.FILL, MenuTheme.FILL_HOVER,
				MenuTheme.FILL_PRESSED, MenuTheme.FILL_ACTIVE, MenuTheme.FILL_DISABLED, MenuTheme.WHITE, MenuTheme.GOLD, MenuTheme.GREY,
				MenuTheme.DIM, MenuTheme.SELECTED_ROW, MenuTheme.WARN_FILL, MenuTheme.SCROLL_KNOB))
	}

	@Test
	fun scrollProgressTabs() {
		Assertions.assertEquals(0 to 100, MenuTheme.scrollKnob(100, 10, 5, 0))
		Assertions.assertEquals(0 to 25, MenuTheme.scrollKnob(100, 10, 40, 0))
		Assertions.assertEquals(75 to 25, MenuTheme.scrollKnob(100, 10, 40, 30))
		Assertions.assertEquals(75 to 25, MenuTheme.scrollKnob(100, 10, 40, 99))
		Assertions.assertEquals(0 to 8, MenuTheme.scrollKnob(100, 1, 1000, 0))
		Assertions.assertEquals(50, MenuTheme.progressWidth(100, 5.0, 10.0))
		Assertions.assertEquals(100, MenuTheme.progressWidth(100, 50.0, 10.0))
		Assertions.assertEquals(0, MenuTheme.progressWidth(100, 5.0, 0.0))
		Assertions.assertEquals(listOf(0, 30, 50), MenuTheme.tabOffsets(listOf(20, 10, 30)))
	}

	@Test
	fun confirmGuardCountdownEscTimeout() {
		val g = dev.ashtonite365.apex.features.menus.ConfirmGuard<String>()
		Assertions.assertNull(g.proceed(0))
		g.hold("withdraw all", 1_000)
		Assertions.assertEquals(3L, g.secondsLeft(1_000))
		Assertions.assertNull(g.proceed(3_999)); Assertions.assertEquals("withdraw all", g.pending)
		Assertions.assertEquals("withdraw all", g.proceed(4_000)); Assertions.assertNull(g.pending)
		g.hold("x", 0); g.cancel(); Assertions.assertNull(g.proceed(99_000))
		g.hold("y", 0); Assertions.assertFalse(g.expire(14_999)); Assertions.assertTrue(g.expire(15_000)); Assertions.assertNull(g.pending)
	}

	@Test
	fun bazaarAccentAndPanelHooks() {

		Assertions.assertEquals(dev.ashtonite365.apex.features.menus.style.MenuStyle.palette.theme().accent, MenuCustomisation().palette.accent)
		Assertions.assertEquals(0xFF112233.toInt(), MenuCustomisation(accent = 0xFF112233.toInt()).palette.accent)
	}
}
