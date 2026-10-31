// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.hud

import dev.ashtonite365.apex.gui.config.HudAnchor
import dev.ashtonite365.apex.gui.config.HudEnabled
import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlinx.serialization.json.Json
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.joml.Matrix3x2f
import org.joml.Vector2i
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class HudMetaGoldenTest {
	private fun <T> a(f: () -> T): String = try { f().toString() } catch (t: Throwable) { "ERR:" + t.javaClass.simpleName }

	private fun describe(m: HudMeta): String = listOf(
		"pos=${m.position}", "uw=${a { m.unscaledWidth }}", "uh=${a { m.unscaledHeight }}", "dw=${a { m.drawnWidth }}", "dh=${a { m.drawnHeight }}",
		"tl=${a { m.topLeft }}", "sx=${a { m.scaleX }}", "sy=${a { m.scaleY }}", "u=${a { m.uniformScale }}", "st=${a { m.isStretched }}",
		"lbl=${a { m.showsEditorLabel }}", "en=${a { m.isEnabled }}",
	).joinToString(" ")

	private val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
	private fun field(owner: Class<*>, name: String) = owner.getDeclaredField(name).apply { isAccessible = true }

	private fun <T> withGui(w: Int, h: Int, body: () -> T): T {
		val mc = unsafe.allocateInstance(net.minecraft.client.Minecraft::class.java)
		val window = unsafe.allocateInstance(com.mojang.blaze3d.platform.Window::class.java)
		field(com.mojang.blaze3d.platform.Window::class.java, "guiScaledWidth").setInt(window, w)
		field(com.mojang.blaze3d.platform.Window::class.java, "guiScaledHeight").setInt(window, h)
		field(net.minecraft.client.Minecraft::class.java, "window").set(mc, window)
		val inst = field(net.minecraft.client.Minecraft::class.java, "instance")
		val before = inst.get(null)
		inst.set(null, mc)
		try { return body() } finally { inst.set(null, before) }
	}

	@Test
	fun hudMetaBehaviour() {
		val out = mutableListOf<String>()
		for ((gw, gh) in listOf(480 to 270, 640 to 360)) withGui(gw, gh) { out += "== gui $gw x $gh"; body(out) }
		OursGolden.check("hudmeta-behaviour", out)
	}

	private fun body(out: MutableList<String>) {
		val saved = HudEnabled.lookup
		try {
			HudEnabled.lookup = { it.endsWith("on") }
			fun meta(pos: HudPosition, key: String, w: Int, h: Int, label: Component = Component.translatable(key)) =
				HudMeta(pos, Identifier.fromNamespaceAndPath("apex", key.substringAfterLast('.').replace('-', '_')), label, w, h)
			val positions = listOf(
				{ HudPosition(10, 20, 1f) }, { HudPosition(-5, 7, 1.5f, HudAnchor.TOP_RIGHT) }, { HudPosition(3, -30, 0.75f, HudAnchor.HOTBAR) },
				{ HudPosition(0, 0, 1f, HudAnchor.CENTRE, 2f, 0.5f) }, { HudPosition(400, 200, 2f, HudAnchor.BOTTOM_RIGHT) },
			)
			for ((pi, pf) in positions.withIndex()) for ((w, h) in listOf(100 to 20, 41 to 17, 0 to 0)) {
				for (key in listOf("apex.config.t.on", "apex.config.t.off")) {
					val m = meta(pf(), key, w, h)
					out += "p$pi ${w}x$h $key: " + describe(m) + " str=${a { m }} hash=${a { m.hashCode() == meta(pf(), key, w, h).hashCode() }} eq=${m == meta(pf(), key, w, h)}"
				}
				val key = "apex.config.t.on"
				val ops: List<Pair<String, (HudMeta) -> Any?>> = listOf(
					"moveTo(30,40)" to { it.moveTo(30, 40) }, "moveTo(-10,500)" to { it.moveTo(-10, 500) },
					"uniform(2)" to { it.uniformScale = 2f }, "uniform(0.5)" to { it.uniformScale = 0.5f },
					"scales(2,1)" to { it.setScales(2f, 1f) }, "scales(1.25,1.25)" to { it.setScales(1.25f, 1.25f) },
					"frame(1.5,0.8,12,34)" to { it.resizeFrame(1.5f, 0.8f, 12, 34) },
					"frame+finish" to { it.resizeFrame(1.3f, 1.3f, 100, 100); it.finishResize() },
					"finish" to { it.finishResize() }, "reset" to { it.resetSize() },
					"textFit(1,1)" to { it.textFit(1f, 1f) }, "textFit(200,9)" to { it.textFit(200f, 9f) }, "textFit(5,50)" to { it.textFit(5f, 50f) },
					"apply(80,10)" to { val mx = Matrix3x2f(); val f = it.applyTextTransformations(mx, 80f, 10f); "$f $mx" },
					"applyTo" to { val mx = Matrix3x2f(); it.applyTo(mx); mx.toString() },
				)
				for ((name, op) in ops) {
					val m = meta(pf(), key, w, h)
					val r = a { op(m) }
					out += "  $name -> $r | " + describe(m)
				}
				val chain = meta(pf(), key, w, h)
				for ((name, op) in ops) { val r = a { op(chain) }; out += "  chain $name -> $r | " + describe(chain) }
			}

			val lit = HudMeta(HudPosition(1, 2, 1f), Identifier.fromNamespaceAndPath("apex", "lit"), Component.literal("Plain"), 10, 10)
			out += "literal: " + describe(lit)

			for (k in listOf("apex.config.t.on", "apex:on", "on", "t.on")) {
				HudMeta.sizeOverrides[k] = { Vector2i(77, 33) }
				try {
					val m = meta(HudPosition(5, 5, 2f), "apex.config.t.on", 10, 10)
					out += "override $k: " + describe(m)
				} finally { HudMeta.sizeOverrides.remove(k) }
			}
			HudMeta.sizeOverrides["apex.config.t.on"] = { error("boom") }
			try { out += "override throws: " + describe(meta(HudPosition(5, 5, 1f), "apex.config.t.on", 10, 10)) } finally { HudMeta.sizeOverrides.remove("apex.config.t.on") }
			HudMeta.sizeOverrides["apex.config.t.on"] = { Vector2i(0, -4) }
			try { out += "override zero: " + describe(meta(HudPosition(5, 5, 1f), "apex.config.t.on", 10, 10)) } finally { HudMeta.sizeOverrides.remove("apex.config.t.on") }
			HudEnabled.lookup = { error("x") }
			out += "lookup throws: " + describe(meta(HudPosition(5, 5, 1f), "apex.config.t.thr", 10, 10))
		} finally { HudEnabled.lookup = saved }

		for (j in listOf("""{"x":1,"y":2,"scale":1.5}""", """{"x":1,"y":2}""", """{"x":1,"y":2,"scale":1.0,"anchor":"hotbar","scaleX":2.0}""", """{"x":1,"y":2,"scale":2.0,"scaleY":3.0}"""))
			out += "json $j -> ${a { Json.decodeFromString(HudPosition.serializer(), j) }} sx=${a { Json.decodeFromString(HudPosition.serializer(), j).effectiveScaleX }} sy=${a { Json.decodeFromString(HudPosition.serializer(), j).effectiveScaleY }}"
		for (anchor in listOf(null) + HudAnchor.entries) for ((sw, sh) in listOf(480 to 270, 100 to 50)) {
			val p = HudPosition(17, 23, 1f, anchor)
			out += "pos $anchor $sw x $sh resolve=${a { p.resolve(30, 10, sw, sh) }}"
			p.migrate(30, 10, sw, sh); out += "  migrate -> $p"
			p.place(200, 100, 30, 10, sw, sh); out += "  place -> $p resolve=${a { p.resolve(30, 10, sw, sh) }}"
			p.dragTo(0.6f, 1.7f, 50, 60, 30, 10, sw, sh); out += "  drag -> $p"
			p.setUniformScale(0f); out += "  uni0 -> $p"
		}
	}
}
