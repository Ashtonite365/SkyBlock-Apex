// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.hud.editor

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.gui.config.HudHidden

class HudEditorScreen(
	private val returnTo: Screen?,
	huds: List<PlacedHud>,
	snapping: HudSnapping?,
	private val whenClosed: () -> Unit = {},
) : Screen(Component.translatable("apex.hud-editor.title")) {
	val session = HudEditSession(huds, snapping) { width to height }
	private val fades = java.util.IdentityHashMap<PlacedHud, HoverFade>()

	private fun shiftHeld(): Boolean =
		InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)

	private fun mouse(): Pair<Double, Double> {
		val mc = minecraft ?: return 0.0 to 0.0
		return mc.mouseHandler.getScaledXPos(mc.window) to mc.mouseHandler.getScaledYPos(mc.window)
	}

	private fun toolbarText(): List<String> =
		if (!toolbarOpen) listOf("§bHUD Editor §7[+]")
		else listOf(
			"§bHUD Editor §7[–]",
			"§e[Reset all sizes]",
			"§7Drag: move · Arrows: move 1 px (shift 10)",
			"§7Handles: resize (corner = proportional, shift = free)",
			"§7Right-drag: scale · R / middle click: reset size",
			"§7Shift while dragging: no snap",
			"§7Eye button: hide / show in game",
		)

	private fun toolbarBox(): IntArray? {
		val font = minecraft?.font ?: return null
		val lines = toolbarText()
		val textW = lines.maxOf { font.width(it) }
		val pad = EditorLayout.TOOLBAR_PADDING
		return EditorLayout.toolbarRect(textW + 2 * pad, lines.size * TOOLBAR_LINE + 2 * pad - 1, width, height,
			session.shown().map(session::rectOf))
	}

	private fun toolbarClicked(x: Double, y: Double): Boolean {
		val box = toolbarBox()
		if (!EditorLayout.contains(box, x, y)) return false
		val row = Math.floor((y - box!![1] - EditorLayout.TOOLBAR_PADDING + 1) / TOOLBAR_LINE).toInt()
		when {
			row <= 0 -> toolbarOpen = !toolbarOpen
			row == 1 && toolbarOpen -> session.shown().forEach(session::resetSize)
		}
		return true
	}

	private fun drawToolbar(g: GuiGraphicsExtractor, mx: Int, my: Int) {
		val box = toolbarBox() ?: return
		val font = minecraft!!.font
		val lit = EditorLayout.contains(box, mx.toDouble(), my.toDouble()) && session.hudAt(mx.toDouble(), my.toDouble()) == null
		g.fill(box[0], box[1], box[0] + box[2], box[1] + box[3], if (lit) 0xA0000000.toInt() else 0x70000000)
		g.outline(box[0], box[1], box[2], box[3], 0x80606060.toInt())
		for ((i, text) in toolbarText().withIndex()) {
			val top = box[1] + EditorLayout.TOOLBAR_PADDING + i * TOOLBAR_LINE
			val rowLit = lit && i <= 1 && my >= top - 1 && my < top + TOOLBAR_LINE - 1
			val shown = if (rowLit) "§6" + text.replace(FORMAT_CODE, "") else text
			g.text(font, Component.literal(shown), box[0] + EditorLayout.TOOLBAR_PADDING, top, -1, false)
		}
	}

	override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(g, mouseX, mouseY, delta)
		val font = minecraft?.font ?: return
		drawToolbar(g, mouseX, mouseY)
		val shown = session.shown()
		val mx = mouseX.toDouble()
		val my = mouseY.toDouble()
		val hovered = session.held ?: session.hudAt(mx, my)
		for (hud in shown) {
			g.pose().pushMatrix()
			hud.applyTo(g.pose())
			val fade = fades.getOrPut(hud) { HoverFade(HOVER_FADE_MS) }
			fade.towards(if (hud === hovered) 1.0 else 0.0)
			g.fill(0, 0, hud.unscaledWidth, hud.unscaledHeight, 0x14FFFFFF)
			val edge = if (hud === session.selected) SELECTED else fade.blend(IDLE_EDGE, HOVER_EDGE)
			g.outline(0, 0, hud.unscaledWidth, hud.unscaledHeight, edge)
			if (HudHidden.isHidden(hud.hudId, hud.label)) g.fill(0, 0, hud.unscaledWidth, hud.unscaledHeight, HIDDEN_SHADE)
			g.pose().popMatrix()
		}
		for (hud in shown) drawEye(g, hud, mouseX, mouseY)
		for (hud in shown) {
			if (!hud.showsEditorLabel) continue
			val r = session.rectOf(hud)
			val textW = font.width(hud.label)
			val at = EditorLayout.labelSpot(r[0], r[1], r[2], r[3], textW, font.lineHeight, width, height)
			g.fill(at[0] - 1, at[1] - 1, at[0] + textW + 1, at[1] + font.lineHeight, 0xC0000000.toInt())
			g.text(font, hud.label, at[0], at[1], -1, false)
		}
		val active = session.resizingHud ?: hovered
		val half = HudGeometry.GRIP_PX / 2
		for (hud in shown) {
			if (hud !is ResizableHud) continue
			val r = session.rectOf(hud)
			val colour = if (hud === active) SELECTED else 0xC0A0A0A0.toInt()
			for (grip in HudGeometry.Grip.entries) {
				val cx = Math.round(grip.centreX(r[0].toDouble(), r[2].toDouble())).toInt()
				val cy = Math.round(grip.centreY(r[1].toDouble(), r[3].toDouble())).toInt()
				g.fill(cx - half, cy - half, cx + half, cy + half, colour)
			}
		}
		if (session.held != null && !session.isScaling) {
			session.guideX?.let { g.fill(it, 0, it + 1, height, GUIDE) }
			session.guideY?.let { g.fill(0, it, width, it + 1, GUIDE) }
		}
	}

	private fun eyeAt(x: Double, y: Double): PlacedHud? =
		session.shown().lastOrNull { EditorLayout.contains(EditorLayout.eyeRect(session.rectOf(it), width), x, y) }

	private fun drawEye(g: GuiGraphicsExtractor, hud: PlacedHud, mx: Int, my: Int) {
		val e = EditorLayout.eyeRect(session.rectOf(hud), width)
		val lit = EditorLayout.contains(e, mx.toDouble(), my.toDouble())
		val hidden = HudHidden.isHidden(hud.hudId, hud.label)
		g.fill(e[0], e[1], e[0] + e[2], e[1] + e[3], if (lit) 0xE0000000.toInt() else 0xA0000000.toInt())
		val ink = if (hidden) 0xFF808080.toInt() else if (lit) SELECTED else 0xFFE0E0E0.toInt()
		val cx = e[0] + e[2] / 2; val cy = e[1] + e[3] / 2
		g.fill(cx - 3, cy - 1, cx + 4, cy, ink)
		g.fill(cx - 3, cy + 1, cx + 4, cy + 2, ink)
		g.fill(cx - 4, cy, cx - 3, cy + 1, ink); g.fill(cx + 4, cy, cx + 5, cy + 1, ink)
		if (!hidden) g.fill(cx - 1, cy - 1, cx + 2, cy + 2, ink)
		else for (i in -3..3) g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, 0xFFFF5555.toInt())
	}

	override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (minecraft?.level == null) super.extractBackground(g, mouseX, mouseY, delta)
	}

	override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
		val handled = when (event.button()) {
			InputConstants.MOUSE_BUTTON_LEFT -> eyeAt(event.x(), event.y())?.let { HudHidden.toggle(it.hudId, it.label); true }
				?: session.pressLeft(event.x(), event.y(), ::toolbarClicked)
			InputConstants.MOUSE_BUTTON_RIGHT -> session.pressRight(event.x(), event.y())
			InputConstants.MOUSE_BUTTON_MIDDLE -> session.pressMiddle(event.x(), event.y())
			else -> null
		}
		return handled ?: super.mouseClicked(event, doubled)
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean {
		val handled = when (event.button()) {
			InputConstants.MOUSE_BUTTON_LEFT -> session.releaseLeft()
			InputConstants.MOUSE_BUTTON_RIGHT -> session.releaseRight()
			else -> null
		}
		return handled ?: super.mouseReleased(event)
	}

	override fun mouseMoved(x: Double, y: Double) {
		session.move(x, y, ::shiftHeld)
		super.mouseMoved(x, y)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val key = event.key()
		if (key == InputConstants.KEY_R) {
			val (mx, my) = mouse()
			val hud = session.hudAt(mx, my)
			if (hud != null) { session.resetSize(hud); return true }
		}
		val dx = when (key) { InputConstants.KEY_LEFT -> -1; InputConstants.KEY_RIGHT -> 1; else -> 0 }
		val dy = when (key) { InputConstants.KEY_UP -> -1; InputConstants.KEY_DOWN -> 1; else -> 0 }
		if ((dx != 0 || dy != 0) && minecraft != null) {
			val (mx, my) = mouse()
			session.arrow(dx, dy, shiftHeld(), mx, my)
			return true
		}
		return super.keyPressed(event)
	}

	override fun onClose() {
		minecraft?.gui?.setScreen(returnTo)
		whenClosed()
	}

	companion object {
		const val TOOLBAR_LINE = 10
		const val HOVER_FADE_MS = 200.0
		private const val SELECTED = 0xFFFFAA00.toInt()
		private const val IDLE_EDGE = 0xFF6A6D70.toInt()
		private const val HOVER_EDGE = 0xFFC0C0C8.toInt()
		private const val GUIDE = 0xA000FFFF.toInt()

		private const val HIDDEN_SHADE = 0x90303030.toInt()
		private val FORMAT_CODE = dev.ashtonite365.apex.util.SharedRegex.SECTION

		@JvmStatic var toolbarOpen = false
	}
}

class HoverFade(private val spanMs: Double, private val clock: () -> Long = System::currentTimeMillis) {
	private var from = 1.0
	private var to = 0.0
	private var since = 0L

	fun amount(): Double = clamp01(mix(from, to, (clock() - since) / spanMs))

	fun towards(target: Double) {
		from = amount()
		since = clock()
		to = target
	}

	fun blend(a: Int, b: Int): Int {
		val t = amount()
		var out = 0
		for (shift in intArrayOf(24, 16, 8, 0)) out = out or ((mix((a ushr shift) and 0xFF, (b ushr shift) and 0xFF, t) and 0xFF) shl shift)
		return out
	}

	companion object {
		fun mix(a: Int, b: Int, t: Double): Int = ((b - a) * t + a).toInt()
		fun mix(a: Double, b: Double, t: Double): Double = (b - a) * t + a
		fun clamp01(v: Double): Double = maxOf(0.0, minOf(v, 1.0))
	}
}
