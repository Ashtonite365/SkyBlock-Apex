// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.LowFireOverlay
import dev.ashtonite365.apex.features.misc.MiscRender
import dev.ashtonite365.apex.features.misc.MiscRender.FireOverlay
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingsRegistry

class LowFireOverlayTest {
	private fun eq(expected: Float, actual: Float) = assertEquals(expected, actual, 1e-6F)

	private val shift = -0.3F
	private val tan35 = Math.tan(Math.toRadians(35.0)).toFloat()
	private val halfScreen = 0.5F * tan35

	private fun screenFraction(quadY: Float) = (quadY + shift + halfScreen) / (2 * halfScreen)

	@Test
	fun topAtFifthOfScreenHeight() {
		val q = LowFireOverlay.quad(0F, 1F, 16)
		eq(0.2F, screenFraction(q.yTop))
		eq(0.2F, LowFireOverlay.TOP_SCREEN_FRACTION)
		eq(-0.3F, LowFireOverlay.VIEW_SHIFT)
		eq(halfScreen, LowFireOverlay.SCREEN_HALF)
	}

	@Test
	fun topPixelRowTrimmedFromCurrentFrame() {

		val q = LowFireOverlay.quad(0.25F, 0.5F, 16)
		eq(0.25F + 0.25F / 16, q.vTop)
		eq(0.5F, q.vBottom)
		assertEquals(1, LowFireOverlay.TRIM_ROWS)

		eq(0.25F + 0.25F / 32, LowFireOverlay.quad(0.25F, 0.5F, 32).vTop)
		val other = LowFireOverlay.quad(0.75F, 1F, 16)
		eq(0.75F + 0.25F / 16, other.vTop); eq(1F, other.vBottom)
	}

	@Test
	fun fullRemainingTextureSquashedNoOtherCrop() {
		val q = LowFireOverlay.quad(0.25F, 0.5F, 16)

		eq(15F / 16, (q.vBottom - q.vTop) / (0.5F - 0.25F))

		val height = q.yTop - q.yBottom
		assertTrue(height > 0F)
		assertTrue(height < 15F / 16, "squashed down (height $height)")
		eq(LowFireOverlay.TOP_Y, q.yTop); eq(LowFireOverlay.BOTTOM_Y, q.yBottom)

		val none = LowFireOverlay.quad(0F, 1F, 16, trimRows = 0)
		eq(0F, none.vTop)
		val all = LowFireOverlay.quad(0F, 1F, 16, trimRows = 99)
		eq(1F, all.vTop)
		eq(1F, LowFireOverlay.quad(0F, 1F, 0).vTop)
	}

	@Test
	fun bottomAtScreenEdgeNoGap() {
		val q = LowFireOverlay.quad(0F, 1F, 16)
		assertTrue(screenFraction(q.yBottom) <= 0F, "no gap at the bottom edge")

		val corner = (0.5 * Math.cos(Math.toRadians(10.0)) + 0.5 * Math.sin(Math.toRadians(10.0))).toFloat()
		assertTrue(LowFireOverlay.FAR_DEPTH >= corner)
		assertTrue((q.yBottom + shift) / corner < -tan35, "no gap at the far corner either")

		eq(-tan35, (q.yBottom + shift) / LowFireOverlay.FAR_DEPTH)
		assertTrue(screenFraction(q.yBottom) > -0.1F, "bottom within 10% of the screen edge")
	}

	@Test
	fun oldHideToggleFoldsIntoChoice() {
		val on = MiscRender.foldOldHideFire(JsonObject(mapOf("hide-fire-overlay" to JsonPrimitive(true), "zoom-factor" to JsonPrimitive(4))))
		assertEquals(JsonObject(mapOf("zoom-factor" to JsonPrimitive(4), "fire-overlay" to JsonPrimitive("hidden"))), on)
		val off = MiscRender.foldOldHideFire(JsonObject(mapOf("hide-fire-overlay" to JsonPrimitive(false))))
		assertEquals(JsonObject(mapOf()), off)
		val already = MiscRender.foldOldHideFire(JsonObject(mapOf("hide-fire-overlay" to JsonPrimitive(true), "fire-overlay" to JsonPrimitive("low-on-fire"))))
		assertEquals(JsonObject(mapOf("fire-overlay" to JsonPrimitive("low-on-fire"))), already)
		val untouched = JsonObject(mapOf("fire-overlay" to JsonPrimitive("normal")))
		assertSame(untouched, MiscRender.foldOldHideFire(untouched))
	}

	@Test
	fun singleSettingDefaultOffInRenderTab() {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		val opts = MiscRender.TConfig.allOptions
		assertFalse("hide-fire-overlay" in opts, "old toggle merged, not duplicated")
		val opt = opts["fire-overlay"]!!
		assertEquals(FireOverlay.NORMAL, opt.default())
		assertEquals(listOf("normal", "low-on-fire", "hidden"), FireOverlay.entries.map { it.serializedName })
		assertEquals(dev.ashtonite365.apex.gui.config.RiskLevel.NONE, opt.riskLevel)
		assertFalse(opt.labelText.string.startsWith("⚠"))
		val map = MigrationMap.bundled()
		assertEquals(null, map["misc-render.hide-fire-overlay"])
		val e = map["misc-render.fire-overlay"]!!
		assertEquals("render", e.group)
		assertEquals(map["no-fog.crimson-isle"]!!.group, e.group)
		assertEquals(MainSection.MISC, e.newPath.main)
		assertEquals("render", SettingsRegistry.default(map).get(MainSection.MISC, e.newPath.subsection)!!.group)
	}
}
