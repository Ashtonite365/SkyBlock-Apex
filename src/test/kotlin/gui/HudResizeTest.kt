// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry.Grip as Handle

class HudResizeTest {
	private val eps = 1e-4f

	private fun r(h: Handle, dx: Double, dy: Double, free: Boolean = false, sx: Float = 1f, sy: Float = 1f) =
		HudGeometry.resize(h, 50, 40, 100, 20, sx, sy, dx, dy, free, 480, 270)

	@Test
	fun eightHandlesAtCornersAndSides() {
		assertEquals(8, Handle.entries.size)
		assertEquals(4, Handle.entries.count { it.isCorner })
		assertEquals(Handle.TOP_LEFT, HudGeometry.gripAt(50.0, 40.0, 50.0, 40.0, 100.0, 20.0))
		assertEquals(Handle.BOTTOM, HudGeometry.gripAt(100.0, 60.0, 50.0, 40.0, 100.0, 20.0))
		assertEquals(Handle.RIGHT, HudGeometry.gripAt(150.0, 50.0, 50.0, 40.0, 100.0, 20.0))
		assertNull(HudGeometry.gripAt(100.0, 50.0, 50.0, 40.0, 100.0, 20.0))
	}

	@Test
	fun sideStretchesOneAxisOppositeEdgeFixed() {
		val right = r(Handle.RIGHT, 50.0, 30.0)
		assertEquals(1.5f, right.scaleX, eps); assertEquals(1f, right.scaleY, eps)
		assertEquals(50, right.x); assertEquals(40, right.y)
		val left = r(Handle.LEFT, -50.0, 0.0)
		assertEquals(1.5f, left.scaleX, eps); assertEquals(0, left.x)
		val top = r(Handle.TOP, 99.0, -10.0)
		assertEquals(1f, top.scaleX, eps); assertEquals(1.5f, top.scaleY, eps); assertEquals(30, top.y); assertEquals(50, top.x)
	}

	@Test
	fun cornerProportionalShiftFree() {
		val c = r(Handle.BOTTOM_RIGHT, 100.0, 20.0)
		assertEquals(2f, c.scaleX, eps); assertEquals(2f, c.scaleY, eps)
		val tl = r(Handle.TOP_LEFT, -100.0, -20.0)
		assertEquals(2f, tl.scaleX, eps); assertEquals(-50, tl.x); assertEquals(20, tl.y)
		val stretched = r(Handle.BOTTOM_RIGHT, 100.0, 20.0, sx = 2f, sy = 1f)
		assertEquals(2f * stretched.scaleY, stretched.scaleX, eps)
		val free = r(Handle.BOTTOM_RIGHT, 100.0, 0.0, free = true)
		assertEquals(2f, free.scaleX, eps); assertEquals(1f, free.scaleY, eps)
	}

	@Test
	fun minAndMaxSizes() {
		val tiny = r(Handle.RIGHT, -1000.0, 0.0)
		assertEquals(HudGeometry.SMALLEST_SCALE, tiny.scaleX, eps)
		val narrow = HudGeometry.resize(Handle.BOTTOM, 0, 0, 100, 8, 1f, 1f, 0.0, -100.0, false, 480, 270)
		assertEquals(6f / 8f, narrow.scaleY, eps)
		val huge = r(Handle.RIGHT, 1e6, 0.0)
		assertEquals(4.8f, huge.scaleX, eps)
		val bigCorner = r(Handle.BOTTOM_RIGHT, 1e6, 1e6)
		assertEquals(bigCorner.scaleX, bigCorner.scaleY, eps); assertTrue(bigCorner.scaleX <= 4.8f + eps)
		val smallCorner = r(Handle.BOTTOM_RIGHT, -1e6, -1e6)
		assertTrue(100 * smallCorner.scaleX >= 6 - eps && 20 * smallCorner.scaleY >= 6 - eps)
		assertEquals(smallCorner.scaleX, smallCorner.scaleY, eps)
	}

	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun savingIsBackwardCompatible() {
		val old = json.decodeFromString<HudPosition>("""{"x":5,"y":7,"scale":1.5,"anchor":"top-left"}""")
		assertEquals(1.5f, old.effectiveScaleX); assertEquals(1.5f, old.effectiveScaleY)
		assertFalse(Json.encodeToString(old).contains("scaleX"))
		val p = HudPosition(0, 0, 1f, HudAnchor.TOP_LEFT)
		p.setScales(2f, 0.5f)
		val back = Json.decodeFromString<HudPosition>(Json.encodeToString(p))
		assertEquals(2f, back.effectiveScaleX); assertEquals(0.5f, back.effectiveScaleY); assertEquals(1f, back.scale, eps)
		back.setUniformScale(2f)
		assertEquals(4f, back.effectiveScaleX, eps); assertEquals(1f, back.effectiveScaleY, eps)
		back.resetScale()
		assertEquals(1f, back.scale); assertNull(back.scaleX); assertNull(back.scaleY)
		assertFalse(Json.encodeToString(back).contains("scaleX"))
		p.setScales(1.25f, 1.25f); assertNull(p.scaleX); assertEquals(1.25f, p.scale)
	}
}
