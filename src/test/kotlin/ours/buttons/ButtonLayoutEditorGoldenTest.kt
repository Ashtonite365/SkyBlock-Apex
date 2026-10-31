// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.ours.buttons

import dev.ashtonite365.apex.features.inventory.buttons.ButtonLayoutEditor
import dev.ashtonite365.apex.features.inventory.buttons.InventoryButton
import dev.ashtonite365.apex.features.inventory.buttons.InventoryButtons
import dev.ashtonite365.apex.test.golden.HeadlessGui
import dev.ashtonite365.apex.test.ours.OursGolden
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.data.GenericConfig
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class ButtonLayoutEditorGoldenTest {
	private fun <T> a(f: () -> T): String = try { f().toString() } catch (t: Throwable) { "ERR:" + t.javaClass.simpleName + (if (System.getenv("BLE_DBG") != null) " " + t.message + " @ " + t.stackTrace.take(6).joinToString(" < ") else "") }

	private val current = GenericConfig::class.java.getDeclaredField("current").also { it.isAccessible = true }

	private fun start(buttons: List<InventoryButton>, profiles: Map<String, List<InventoryButton>> = mapOf("other" to listOf(InventoryButton(1, 2, true, true, "DIRT", "x")))) {
		current.set(InventoryButtons.DConfig, InventoryButtons.Data(buttons.map { it.copy() }.toMutableList(), "default",
			profiles.mapValues { (_, v) -> v.map { it.copy() }.toMutableList() }.toMutableMap()))
	}

	private fun editor(frame: Rectangle = Rectangle(152, 52, 176, 166)): ButtonLayoutEditor {
		val s = ButtonLayoutEditor(frame)
		fun set(n: String, v: Any?) = Screen::class.java.getDeclaredField(n).also { it.isAccessible = true }.set(s, v)
		set("width", 480); set("height", 270); set("minecraft", HeadlessGui.fakeGame); set("font", HeadlessGui.font)
		Screen::class.java.getDeclaredMethod("rebuildWidgets").also { it.isAccessible = true }.invoke(s)
		return s
	}

	private fun state(s: ButtonLayoutEditor): String {
		val draft = ButtonLayoutEditor::class.java.getDeclaredField("draft").also { it.isAccessible = true }.get(s)
		val press = ButtonLayoutEditor::class.java.getDeclaredField("press").also { it.isAccessible = true }.get(s)
		val pressText = press?.let { p -> listOf("button", "x", "y", "dragging", "changedLayout").joinToString(",") { n -> n + "=" + p.javaClass.getDeclaredField(n).also { it.isAccessible = true }.get(p) } }
		val d = InventoryButtons.DConfig.data
		return "draft=$draft press=$pressText popup=${s.popup != null} saved=${d.buttons} active=${d.activeProfile} profiles=${d.profiles.keys.sorted()}"
	}

	private fun widgets(s: ButtonLayoutEditor) = s.children().filterIsInstance<AbstractWidget>().joinToString("; ") { "${it.javaClass.simpleName} ${it.x},${it.y} ${it.width}x${it.height} '${it.message.string}' active=${it.active}" }
	private fun frame(s: ButtonLayoutEditor, mx: Int = 0, my: Int = 0) = HeadlessGui.record { s.extractRenderState(it, mx, my, 0f) }.joinToString(" | ")

	private fun click(x: Double, y: Double, button: Int = 0, mods: Int = 0) = MouseButtonEvent(x, y, MouseButtonInfo(button, mods))

	@Test
	fun characterize() = HeadlessGui.withFakeGame {
		val out = mutableListOf<String>()
		val saved = current.get(InventoryButtons.DConfig)

		val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
		val window = unsafe.allocateInstance(com.mojang.blaze3d.platform.Window::class.java)
		fun wf(n: String) = com.mojang.blaze3d.platform.Window::class.java.getDeclaredField(n).also { it.isAccessible = true }
		wf("guiScaledWidth").setInt(window, 480); wf("guiScaledHeight").setInt(window, 270)
		val mcWindow = net.minecraft.client.Minecraft::class.java.getDeclaredField("window").also { it.isAccessible = true }
		val prevWindow = mcWindow.get(HeadlessGui.fakeGame)
		mcWindow.set(HeadlessGui.fakeGame, window)
		try {
			val layout = listOf(
				InventoryButton(-20, 0, false, false, "DIRT", "warp hub"),
				InventoryButton(2, -40, true, true, "BONE", "pets", true),
				InventoryButton(0, -20, false, false, "", ""),
				InventoryButton(40, 4, false, true, "skull:abc", "/sbmenu"),
			)
			start(layout)
			val s = editor()
			out += "widgets: " + widgets(s)
			out += "state: " + state(s)
			out += "frame: " + frame(s)
			out += "frame hover: " + frame(s, 140, 60)

			val plays: List<Pair<String, (ButtonLayoutEditor) -> Any?>> = listOf(
				"click+release on button" to { e -> listOf(e.mouseClicked(click(141.0, 61.0), false), e.mouseReleased(click(141.0, 61.0))) },
				"right click on button" to { e -> listOf(e.mouseClicked(click(141.0, 61.0, 1), false), e.mouseReleased(click(141.0, 61.0, 1))) },
				"ctrl click on button" to { e -> listOf(e.mouseClicked(click(141.0, 61.0, 0, 2), false), e.mouseReleased(click(141.0, 61.0, 0, 2))) },
				"small drag" to { e -> listOf(e.mouseClicked(click(141.0, 61.0), false), e.mouseDragged(click(143.0, 62.0), 2.0, 1.0), e.mouseReleased(click(143.0, 62.0))) },
				"drag out" to { e -> listOf(e.mouseClicked(click(141.0, 61.0), false), e.mouseDragged(click(120.0, 40.0), -21.0, -21.0), state(e), e.mouseDragged(click(100.0, 30.0), -20.0, -10.0), e.mouseReleased(click(100.0, 30.0))) },
				"drag onto inventory" to { e -> listOf(e.mouseClicked(click(141.0, 61.0), false), e.mouseDragged(click(200.0, 100.0), 59.0, 39.0), e.mouseReleased(click(200.0, 100.0))) },
				"shift drag (snap)" to { e -> listOf(e.mouseClicked(click(141.0, 61.0), false), e.mouseDragged(click(117.0, 47.0, 0, 1), -24.0, -14.0), e.mouseReleased(click(117.0, 47.0, 0, 1))) },
				"drag big button right" to { e -> listOf(e.mouseClicked(click(340.0, 190.0), false), e.mouseDragged(click(360.0, 120.0), 20.0, -70.0), e.mouseReleased(click(360.0, 120.0))) },
				"click empty outside" to { e -> listOf(e.mouseClicked(click(60.0, 60.0), false), e.mouseReleased(click(60.0, 60.0))) },
				"drag from empty" to { e -> listOf(e.mouseClicked(click(60.0, 60.0), false), e.mouseDragged(click(70.0, 70.0), 10.0, 10.0), e.mouseReleased(click(70.0, 70.0))) },
				"click on inventory" to { e -> listOf(e.mouseClicked(click(200.0, 100.0), false), e.mouseReleased(click(200.0, 100.0))) },
				"click below right" to { e -> listOf(e.mouseClicked(click(400.0, 250.0), false), e.mouseReleased(click(400.0, 250.0))) },
				"escape" to { e -> e.keyPressed(KeyEvent(256, 0, 0)) },
				"key a" to { e -> e.keyPressed(KeyEvent(65, 0, 0)) },
				"onClose" to { e -> e.onClose() },
				"resize" to { e -> e.resize(640, 360) },
			)
			for ((name, play) in plays) {
				start(layout)
				val e = editor()
				val r = a { play(e) }
				out += "$name -> $r"
				out += "  " + state(e)
				out += "  frame " + a { frame(e) }.hashCode() + " widgets " + widgets(e)
			}

			start(layout)
			val count = editor().children().filterIsInstance<AbstractWidget>().size
			for (i in 0 until count) {
				start(layout)
				val e = editor()
				val w = e.children().filterIsInstance<AbstractWidget>()[i]
				val r = a { e.mouseClicked(click(w.x + w.width / 2.0, w.y + w.height / 2.0), false) }
				out += "widget $i '${w.message.string}' -> $r"
				out += "  " + state(e) + " widgets " + widgets(e)
			}

			start(emptyList(), emptyMap())
			val empty = editor(Rectangle(10, 10, 100, 80))
			out += "empty: " + widgets(empty) + " | " + state(empty) + " | " + frame(empty, 50, 50)
		} finally { current.set(InventoryButtons.DConfig, saved); mcWindow.set(HeadlessGui.fakeGame, prevWindow) }
		OursGolden.check("buttonlayouteditor", out)
	}
}
