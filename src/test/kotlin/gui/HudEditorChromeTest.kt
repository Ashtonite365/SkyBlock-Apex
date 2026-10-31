package dev.ashtonite365.apex.test.gui

import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.gui.hud.HudEditorKeys
import dev.ashtonite365.apex.gui.hud.HudSnap
import dev.ashtonite365.apex.gui.hud.editor.HudSnapping
import dev.ashtonite365.apex.gui.hud.editor.PlacedHud
import dev.ashtonite365.apex.gui.hud.editor.ResizableHud
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.gui.hud.editor.EditorLayout
import dev.ashtonite365.apex.gui.hud.editor.EditorLayout.Target as Hit
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudPosition
import org.joml.Vector2i

class HudEditorChromeTest {
	private val sw = 400
	private val sh = 300

	@Test
	fun elementsWinOverToolbar() {
		assertEquals(Hit.HANDLE, EditorLayout.target(true, true, true))
		assertEquals(Hit.ELEMENT, EditorLayout.target(false, true, true))
		assertEquals(Hit.TOOLBAR, EditorLayout.target(false, false, true))
		assertEquals(Hit.NONE, EditorLayout.target(false, false, false))
	}

	@Test
	fun toolbarDefaultsTopLeftWhenFree() {
		val tb = EditorLayout.toolbarRect(60, 15, sw, sh, listOf(intArrayOf(150, 140, 50, 20)))
		assertArrayEquals(intArrayOf(2, 2, 60, 15), tb)
	}

	@Test
	fun toolbarMovesOffElements() {

		val tl = intArrayOf(0, 0, 80, 30)
		assertArrayEquals(intArrayOf(sw - 60 - 2, 2, 60, 15), EditorLayout.toolbarRect(60, 15, sw, sh, listOf(tl)))

		val tr = intArrayOf(330, 0, 70, 30)
		assertArrayEquals(intArrayOf(2, sh - 15 - 2, 60, 15), EditorLayout.toolbarRect(60, 15, sw, sh, listOf(tl, tr)))
	}

	@Test
	fun toolbarPicksLeastCoveredSpotWhenAllTaken() {
		val all = listOf(
			intArrayOf(0, 0, 80, 30), intArrayOf(320, 0, 80, 30), intArrayOf(0, 270, 80, 30),
			intArrayOf(320, 270, 80, 30), intArrayOf(160, 0, 80, 30), intArrayOf(160, 290, 80, 10),
		)
		val tb = EditorLayout.toolbarRect(60, 15, sw, sh, all)
		assertArrayEquals(intArrayOf((sw - 60) / 2, sh - 15 - 2, 60, 15), tb)
	}

	@Test
	fun insideIsHalfOpen() {
		val r = intArrayOf(10, 10, 20, 10)
		assertTrue(EditorLayout.contains(r, 10.0, 10.0))
		assertFalse(EditorLayout.contains(r, 30.0, 15.0))
		assertFalse(EditorLayout.contains(r, 15.0, 20.0))
	}

	@Test
	fun arrowsMoveOnePixelPerPress() {
		val start = intArrayOf(100, 100, 40, 20)
		val adapter = HudSnapping { true }
		assertArrayEquals(intArrayOf(101, 100), adapter.nudge(start, 1, 0, false, sw, sh))
		assertArrayEquals(intArrayOf(99, 100), adapter.nudge(start, -1, 0, false, sw, sh))
		assertArrayEquals(intArrayOf(100, 99), adapter.nudge(start, 0, -1, false, sw, sh))
		assertArrayEquals(intArrayOf(100, 101), adapter.nudge(start, 0, 1, false, sw, sh))

		assertArrayEquals(intArrayOf(101, 100), EditorLayout.stepped(start, 1, 0, 1, sw, sh))
		assertArrayEquals(intArrayOf(100, 99), EditorLayout.stepped(start, 0, -1, 1, sw, sh))

		val r = HudSnap.nudge(HudSnap.Rect(100, 100, 40, 20), 1, 0, false, sw, sh)
		assertEquals(40, r.w); assertEquals(20, r.h)
	}

	@Test
	fun arrowMoveIsClampedOnScreen() {
		val adapter = HudSnapping { true }
		assertArrayEquals(intArrayOf(0, 0), adapter.nudge(intArrayOf(0, 0, 40, 20), -1, -1, false, sw, sh))
		assertArrayEquals(intArrayOf(sw - 40, sh - 20), adapter.nudge(intArrayOf(sw - 40, sh - 20, 40, 20), 1, 1, false, sw, sh))
		assertArrayEquals(intArrayOf(0, 0), EditorLayout.stepped(intArrayOf(0, 0, 40, 20), -1, -1, 1, sw, sh))
		assertArrayEquals(intArrayOf(sw - 40, sh - 20), EditorLayout.stepped(intArrayOf(sw - 40, sh - 20, 40, 20), 1, 1, 1, sw, sh))
	}

	@Test
	fun arrowBoundResizeKeysNeverResizeInEditor() {
		val right = SavedKeyBinding.keyWithoutMods(InputConstants.KEY_RIGHT)
		val k = SavedKeyBinding.keyWithoutMods(InputConstants.KEY_K)
		assertTrue(HudEditorKeys.isArrow(right))
		assertTrue(HudEditorKeys.isArrow(SavedKeyBinding.keyWithoutMods(InputConstants.KEY_UP)))
		assertFalse(HudEditorKeys.isArrow(k))
		assertFalse(HudEditorKeys.isArrow(SavedKeyBinding.unbound()))

		assertEquals(listOf(false, true), HudEditorKeys.resizeFires(listOf(true, true), listOf(false, false), listOf(true, false)))

		assertEquals(listOf(false, false), HudEditorKeys.resizeFires(listOf(true, true), listOf(true, true), listOf(false, false)))
	}

	private class PlainHud(pos: Vector2i, w: Int, h: Int) : TestHud(pos, w, h)
	private class ScaledHud(pos: Vector2i, w: Int, h: Int, sx: Float, sy: Float) : TestScaledHud(pos, w, h, sx, sy)

	private fun rect(h: PlacedHud) = intArrayOf(h.topLeft.x(), h.topLeft.y(), h.drawnWidth, h.drawnHeight)

	@Test
	fun arrowMoveWorksForEveryElementType() {
		val snap = HudSnapping { true }
		val kinds = listOf<() -> PlacedHud>(
			{ PlainHud(Vector2i(100, 100), 80, 10) },
			{ ScaledHud(Vector2i(100, 100), 90, 5, 1f, 1f) },
			{ ScaledHud(Vector2i(100, 100), 80, 80, 1.5f, 1.5f) },
			{ ScaledHud(Vector2i(100, 100), 100, 16, 2.2f, 0.6f) },
		)
		for (make in kinds) for (snapper in listOf(snap, null)) for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
			val hud = make()
			val before = rect(hud)
			val scales = (hud as? ResizableHud)?.let { it.scaleX to it.scaleY }
			EditorLayout.arrowMove(hud, before, dx, dy, false, snapper, sw, sh)
			val after = rect(hud)
			val tag = "${hud.javaClass.simpleName} ${before.toList()} d=$dx,$dy snapper=${snapper != null}"
			assertEquals(before[0] + dx, after[0], tag); assertEquals(before[1] + dy, after[1], tag)
			assertEquals(before[2], after[2], "width changed: $tag"); assertEquals(before[3], after[3], "height changed: $tag")
			assertEquals(scales, (hud as? ResizableHud)?.let { it.scaleX to it.scaleY }, "scale changed: $tag")
		}
	}

	@Test
	fun arrowMoveClampsEveryElementTypeOnScreen() {
		for (snapper in listOf(HudSnapping { true }, null)) {
			val bar = ScaledHud(Vector2i(0, 0), 100, 16, 2.2f, 0.6f)
			EditorLayout.arrowMove(bar, rect(bar), -1, -1, false, snapper, sw, sh)
			assertEquals(Vector2i(0, 0), bar.pos)
			val w = bar.drawnWidth; val h = bar.drawnHeight
			bar.pos = Vector2i(sw - w, sh - h)
			EditorLayout.arrowMove(bar, rect(bar), 1, 1, false, snapper, sw, sh)
			assertEquals(Vector2i(sw - w, sh - h), bar.pos)
			val text = PlainHud(Vector2i(sw - 80, 0), 80, 10)
			EditorLayout.arrowMove(text, rect(text), 1, -1, false, snapper, sw, sh)
			assertEquals(Vector2i(sw - 80, 0), text.pos)
		}
	}

	@Test
	fun arrowMoveSavesThroughTheAnchoredPositionLikeADrag() {

		val spots = listOf(10 to 10, 180 to 5, 330 to 10, 5 to 140, 180 to 140, 320 to 140, 10 to 270, 160 to 260, 320 to 270)
		val scaleSets = listOf(1f to 1f, 1.5f to 1.5f, 2.2f to 0.6f)
		val anchorsSeen = mutableSetOf<HudAnchor>()
		for ((x, y) in spots) for ((sx, sy) in scaleSets) for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
			val uw = 30; val uh = 10
			val pos = HudPosition(x, y, 1f); pos.setScales(sx, sy)
			val w = (uw * pos.effectiveScaleX).toInt(); val h = (uh * pos.effectiveScaleY).toInt()
			pos.place(x, y, w, h, sw, sh)
			anchorsSeen += pos.anchor!!
			val start = pos.resolve(w, h, sw, sh)
			val n = HudSnapping { true }.nudge(intArrayOf(start.x, start.y, w, h), dx, dy, false, sw, sh)
			pos.place(n[0], n[1], w, h, sw, sh)
			val end = pos.resolve(w, h, sw, sh)
			val tag = "at $x,$y s=$sx/$sy d=$dx,$dy anchor=${pos.anchor}"
			assertEquals(start.x + dx, end.x, tag); assertEquals(start.y + dy, end.y, tag)
			assertEquals(sx, pos.effectiveScaleX, tag); assertEquals(sy, pos.effectiveScaleY, tag)
		}
		assertTrue(anchorsSeen.size >= 5, "covered anchors: $anchorsSeen")
	}
}
