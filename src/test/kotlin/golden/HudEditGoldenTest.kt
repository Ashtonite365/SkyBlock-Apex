// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.gui.hud.editor.EditorLayout
import dev.ashtonite365.apex.gui.hud.editor.HoverFade
import dev.ashtonite365.apex.gui.hud.editor.HudEditSession
import dev.ashtonite365.apex.gui.hud.editor.HudGeometry
import dev.ashtonite365.apex.gui.hud.editor.OptionSearch
import dev.ashtonite365.apex.gui.hud.editor.PlacedHud
import dev.ashtonite365.apex.gui.hud.editor.ResizableHud
import dev.ashtonite365.apex.gui.hud.editor.SearchableOption
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.joml.Vector2i
import org.joml.Vector2ic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HudEditGoldenTest {
	private val grips = HudGeometry.Grip.entries

	@Test
	fun resizeMaths() {
		Golden.verify("hudedit-resize", HudEditCorpus.resize().map { c ->
			c.key to Golden.run {
				val r = HudGeometry.resize(grips[c.handle], c.x0, c.y0, c.uw, c.uh, c.sx, c.sy, c.dx, c.dy, c.free, c.sw, c.sh)
				"${r.x},${r.y},${r.scaleX},${r.scaleY}"
			}
		}, minCases = 7000)
	}

	@Test
	fun gripsAndBounds() {
		Golden.verify("hudedit-handle-at", HudEditCorpus.handleProbes().map { p ->
			p.joinToString(",") to (HudGeometry.gripAt(p[0], p[1], p[2], p[3], p[4], p[5])?.name ?: "none")
		}, minCases = 3000)
		val bounds = mutableListOf<Pair<String, String>>()
		for (u in listOf(-3, 0, 1, 5, 6, 7, 24, 25, 100, 480, 2000)) for (scr in listOf(0, 1, 60, 270, 480)) for (s in listOf(-1f, 0f, 0.1f, 0.25f, 0.3f, 1f, 4.5f, 6f, 9f))
			bounds += "u$u scr$scr s$s" to "${HudGeometry.lowestScale(u)},${HudGeometry.highestScale(u, scr)},${HudGeometry.limitScale(s, u, scr)}"
		Golden.verify("hudedit-scale-bounds", bounds, minCases = 495)
		Golden.verify("hudedit-handle-centres", grips.flatMap { g ->
			listOf(0.0 to 0.0, 10.0 to 7.0, 33.5 to 101.25).map { (a, b) -> "${g.name} $a $b" to "${g.centreX(a, b)},${g.centreY(b, a)},${g.isCorner},${g.pullX},${g.pullY}" }
		}, minCases = 24)
	}

	@Test
	fun layout() {
		Golden.verify("hudedit-toolbar", HudEditCorpus.toolbars().map { (d, els) ->
			"${HudEditCorpus.fmt(d)}|${els.joinToString(";") { HudEditCorpus.fmt(it) }}" to HudEditCorpus.fmt(EditorLayout.toolbarRect(d[0], d[1], d[2], d[3], els))
		}, minCases = 2000)

		val rr = Random(9)
		Golden.verify("hudedit-nudge", HudEditCorpus.rects(5, 1500).map { rect ->
			val dx = rr.nextInt(-1, 2); val dy = rr.nextInt(-1, 2); val step = listOf(1, 10)[rr.nextInt(2)]; val sw = listOf(480, 200, 30)[rr.nextInt(3)]; val sh = listOf(270, 100, 20)[rr.nextInt(3)]
			"${HudEditCorpus.fmt(rect)} $dx $dy $step $sw $sh" to HudEditCorpus.fmt(EditorLayout.stepped(rect, dx, dy, step, sw, sh))
		}, minCases = 1500)
		Golden.verify("hudedit-inside", HudEditCorpus.rects(6, 1500).map { rect ->
			val mx = rr.nextInt(-50, 500) + listOf(0.0, 0.5, 0.999)[rr.nextInt(3)]; val my = rr.nextInt(-50, 300).toDouble()
			"${HudEditCorpus.fmt(rect)} $mx $my" to EditorLayout.contains(rect, mx, my).toString()
		} + listOf("null" to EditorLayout.contains(null, 0.0, 0.0).toString()), minCases = 1501)
		Golden.verify("hudedit-hit", (0 until 8).map { i -> "$i" to EditorLayout.target(i and 4 != 0, i and 2 != 0, i and 1 != 0).name }, minCases = 8)
		Golden.verify("hudedit-label-pos", HudEditCorpus.rects(7, 2000).map { rect ->
			val tw = rr.nextInt(0, 200); val sw = listOf(480, 100)[rr.nextInt(2)]; val sh = listOf(270, 40)[rr.nextInt(2)]
			"${HudEditCorpus.fmt(rect)} $tw $sw $sh" to HudEditCorpus.fmt(EditorLayout.labelSpot(rect[0], rect[1], rect[2], rect[3], tw, 9, sw, sh))
		}, minCases = 2000)
		Golden.verify("hudedit-screen-rect", List(500) {
			val m = Matrix3x2f().translate(rr.nextInt(-100, 400).toFloat(), rr.nextInt(-100, 300).toFloat()).scale(rr.nextInt(10, 400) / 100f, rr.nextInt(10, 400) / 100f)
			val l = rr.nextInt(0, 50).toFloat(); val t = rr.nextInt(0, 50).toFloat(); val rgt = l + rr.nextInt(0, 200); val b = t + rr.nextInt(0, 100)
			"$m $l $t $rgt $b" to EditorLayout.onScreen(m, l, t, rgt, b).joinToString(",")
		}, minCases = 500)
		Golden.verify("hudedit-lerp", List(300) {
			val a = rr.nextInt(0, 256); val b = rr.nextInt(0, 256); val p = rr.nextDouble(-0.2, 1.2)
			"$a $b $p" to "${HoverFade.mix(a, b, p)},${HoverFade.mix(a.toDouble(), b.toDouble(), p)},${HoverFade.clamp01(p)}"
		}, minCases = 300)
	}

	@Test
	fun scalingPivot() {
		fun quarterName(left: Boolean, top: Boolean): String {
			val p = HudEditSession.pivotFor(intArrayOf(0, 0, 10, 10), if (left) 1.0 else 9.0, if (top) 1.0 else 9.0)

			return (if (p.fy == 1f) "TOP" else "BOTTOM") + "_" + (if (p.fx == 1f) "LEFT" else "RIGHT")
		}
		val n = Golden.verify("hudedit-anchor", listOf(true, false).flatMap { l -> listOf(true, false).map { t -> "q$l$t" to quarterName(l, t) } },
			minCases = 4, only = { it.startsWith("q") })
		assertEquals(4, n)

		val corners = mapOf("TOP_LEFT" to (0.0 to 0.0), "TOP_RIGHT" to (1.0 to 0.0), "BOTTOM_LEFT" to (0.0 to 1.0), "BOTTOM_RIGHT" to (1.0 to 1.0))
		val recorded = Golden.load("hudedit-anchor").toMap()
		for ((name, f) in corners) {
			val grabX = if (f.first == 1.0) 1.0 else 30.0
			val grabY = if (f.second == 1.0) 1.0 else 16.0
			val p = HudEditSession.pivotFor(intArrayOf(10, 20, 31, 17), grabX, grabY)
			assertEquals("Point[x=${p.x}, y=${p.y}]", recorded.getValue("TOP_LEFT->$name").substringAfter(",0.0,0.0,"), name)
		}
	}

	private open class Fake(val id: Int, var pos: Vector2i, val w: Int, val h: Int, val on: Boolean) : PlacedHud {
		override val hudId: Identifier = Identifier.fromNamespaceAndPath("apex", "e$id")
		override val label: Component = Component.literal("e$id")
		override val isEnabled: Boolean get() = on
		override val unscaledWidth: Int get() = w
		override val unscaledHeight: Int get() = h
		override val topLeft: Vector2ic get() = pos
		override fun moveTo(x: Int, y: Int) { pos = Vector2i(x, y) }
		open fun state() = "${pos.x},${pos.y}"
	}

	private class FakeScaled(id: Int, pos: Vector2i, w: Int, h: Int, on: Boolean, override var uniformScale: Float) : Fake(id, pos, w, h, on), ResizableHud {
		override fun state() = "${pos.x},${pos.y},$uniformScale"
	}

	@Test
	fun mouseSessions() {

		val fellThrough = "false"
		val out = mutableListOf<Pair<String, String>>()
		for ((si, s) in HudEditSessions.sessions().withIndex()) {
			val huds: List<Fake> = s.els.mapIndexed { i, e -> if (e.scale == null) Fake(i, Vector2i(e.x, e.y), e.w, e.h, e.enabled) else FakeScaled(i, Vector2i(e.x, e.y), e.w, e.h, e.enabled, e.scale) }
			val session = HudEditSession(huds, null) { s.screenW to s.screenH }
			val log = StringBuilder()
			for (ev in s.events) {
				if (session.resizingWith != null && ev is HudEditSessions.Move) continue
				val ret = when (ev) {
					is HudEditSessions.Press -> (if (ev.button == 0) session.pressLeft(ev.x, ev.y) else session.pressRight(ev.x, ev.y)).toString()
					is HudEditSessions.Move -> { session.move(ev.x, ev.y) { false }; "-" }
					is HudEditSessions.Release -> (if (ev.button == 0) session.releaseLeft() else session.releaseRight())?.toString() ?: fellThrough
				}
				log.append(ret).append(" g=").append(huds.indexOf(session.held)).append(" t=").append(huds.indexOf(session.selected))
					.append(" r=").append(session.resizingWith?.name ?: "-")
					.append(" [").append(huds.joinToString(" ") { it.state() }).append("]\n")
			}
			out += "session$si ${s}" to log.toString().trimEnd()
		}
		val recorded = Golden.load("hudedit-sessions").joinToString("\n") { it.second }
		org.junit.jupiter.api.Assertions.assertFalse("THROWS" in recorded, "fixture must not be degenerate")
		org.junit.jupiter.api.Assertions.assertTrue(recorded.split("\n").count { it.startsWith("true") } > 500)
		Golden.verify("hudedit-sessions", out, minCases = 400)
	}

	@Test
	fun optionSearch() {
		val opts = OptionSearchCorpus.options.map { o ->
			SearchableOption(Component.literal(o.title), o.category?.let { Component.literal(it) }, o.description.map { Component.literal(it) },
				alsoMatches = { it in o.bonus }, open = { error("no") })
		}
		Golden.verify("hudedit-option-search", OptionSearchCorpus.queries.map { q ->
			"[$q]" to Golden.run { OptionSearch.filter(opts, OptionSearchCorpus.modName, q).joinToString(";") { opts.indexOf(it).toString() } }
		}, minCases = 43)
	}
}
