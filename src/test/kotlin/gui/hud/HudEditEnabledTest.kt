// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui.hud

import dev.ashtonite365.apex.gui.hud.editor.HudEditSession
import dev.ashtonite365.apex.gui.hud.editor.HudEditorScreen
import dev.ashtonite365.apex.gui.hud.editor.HudEditors
import dev.ashtonite365.apex.gui.hud.editor.PlacedHud
import net.minecraft.network.chat.Component
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudEnabled
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class HudEditEnabledTest {
	private val toggles = mutableMapOf<String, Boolean>()
	private val savedLookup = HudEnabled.lookup

	private fun meta(config: String, prop: String) = HudMeta(HudPosition(10, 10, 1f), net.minecraft.resources.Identifier.fromNamespaceAndPath("apex", prop),
		Component.translatable("apex.config.$config.$prop"), 100, 20)

	private val day = meta("hud", "day-count-hud")
	private val fps = meta("hud", "fps-count-hud")
	private val bars = meta("stat-bars", "mana-hud")
	private val all: List<PlacedHud> = listOf(day, fps, bars)

	init {
		val rules = mapOf(
			"apex.config.hud.day-count-hud" to HudEnabled.rule("hud", "day-count-hud", setOf("day-count", "fps-count", "editor-snap")),
			"apex.config.hud.fps-count-hud" to HudEnabled.rule("hud", "fps-count-hud", setOf("day-count", "fps-count", "editor-snap")),
			"apex.config.stat-bars.mana-hud" to HudEnabled.rule("stat-bars", "mana-hud", setOf("enabled", "show-mana", "show-health")),
		)
		HudEnabled.lookup = { key -> HudEnabled.evaluate(rules[key]) { toggles[it] } }
	}

	@AfterEach fun restore() { HudEnabled.lookup = savedLookup }

	@Test fun rulesFromTheElementsOwnToggleAndParent() {
		assertEquals(HudEnabled.Rule(listOf("day-count"), null), HudEnabled.rule("hud", "day-count-hud", setOf("day-count", "fps-count")))
		assertEquals(HudEnabled.Rule(listOf("tracker-hud"), null), HudEnabled.rule("bazaar-orders", "tracker-hud-position", setOf("tracker-hud", "orders-hud")))
		assertEquals(HudEnabled.Rule(listOf("enabled"), null), HudEnabled.rule("dungeon-map", "hud", setOf("enabled", "border")))
		assertEquals(HudEnabled.Rule(listOf("hud"), "enabled"), HudEnabled.rule("jerry-helper", "hud-position", setOf("enabled", "hud")))
		assertEquals(HudEnabled.Rule(listOf("show-mana"), "enabled"), HudEnabled.rule("stat-bars", "mana-hud", setOf("enabled", "show-mana")))
		assertEquals(HudEnabled.Rule(listOf("image-enabled"), "links-enabled"), HudEnabled.rule("chat-links", "position", setOf("links-enabled", "image-enabled")))
		assertEquals(HudEnabled.Rule(listOf("nonblank:custom-text"), null), HudEnabled.rule("gap-misc", "custom-text-hud", setOf("clock"), setOf("clock", "custom-text")))
		assertNull(HudEnabled.rule("garden-tools", "crop-speed-meter-position", setOf("farming-hud")), "no toggle: always shown")
		assertTrue(HudEnabled.evaluate(null) { null })
		assertFalse(HudEnabled.evaluate(HudEnabled.Rule(listOf("nonblank:custom-text"), null)) { "  " })
		assertTrue(HudEnabled.evaluate(HudEnabled.Rule(listOf("nonblank:custom-text"), null)) { "hi" })
		assertFalse(HudEnabled.evaluate(HudEnabled.Rule(listOf("show-mana"), "enabled")) { mapOf("show-mana" to true, "enabled" to false)[it] }, "parent off")
	}

	@Test fun disabledExcludedEnabledIncluded() {
		toggles.putAll(mapOf("day-count" to true, "fps-count" to false, "enabled" to true, "show-mana" to true))
		assertEquals(listOf(day, bars), HudEditSession.visibleOf(all))
		assertTrue(day.isEnabled); assertFalse(fps.isEnabled)
	}

	@Test fun toggleChangeRefreshesTheList() {
		toggles.putAll(mapOf("day-count" to false, "fps-count" to false, "enabled" to true, "show-mana" to true))
		assertEquals(listOf(bars), HudEditSession.visibleOf(all))
		toggles["fps-count"] = true
		assertEquals(listOf(fps, bars), HudEditSession.visibleOf(all))
		toggles["enabled"] = false
		assertEquals(listOf(fps), HudEditSession.visibleOf(all), "parent off hides the bar")
	}

	@Test fun enabledElementsAreDraggableNudgeAndResize() {
		val p = HudPosition(0, 0, 1f, HudAnchor.nearest(40, 50, 100, 20, 640, 360))
		p.place(40, 50, 100, 20, 640, 360)
		assertEquals(org.joml.Vector2i(40, 50), p.resolve(100, 20, 640, 360), "drag lands where dropped")
		p.place(41, 50, 100, 20, 640, 360)
		assertEquals(org.joml.Vector2i(41, 50), p.resolve(100, 20, 640, 360))
		p.setScales(1.5f, 1.5f)
		assertEquals(1.5f, p.scale); assertNull(p.scaleX)
		p.setScales(2f, 1f)
		assertEquals(2f, p.effectiveScaleX); assertEquals(1f, p.effectiveScaleY)
	}

	@Test fun rightShiftAndAeOpenTheSameScreen() {
		toggles.putAll(mapOf("day-count" to true, "fps-count" to false, "enabled" to true, "show-mana" to true))

		val open = HudEditors::class.java.getMethod("editor", net.minecraft.client.gui.screens.Screen::class.java, List::class.java)
		assertEquals(HudEditorScreen::class.java, open.returnType)
		val ae = Class.forName("dev.ashtonite365.apex.features.misc.StatBars").declaredMethods.single { it.name == "editorScreen" }
		assertEquals(listOf(net.minecraft.client.gui.screens.Screen::class.java), ae.parameterTypes.toList())
		assertEquals(listOf(day, bars), HudEditSession.visibleOf(all), "the editor shows only the enabled ones")
		val session = HudEditSession(all, null) { 640 to 360 }
		assertEquals(all, session.everyHud(), "the editor gets every HUD")
		assertEquals(listOf(day, bars), session.shown())
	}
}
