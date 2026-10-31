// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.gui

import dev.ashtonite365.apex.gui.hud.editor.PlacedHud
import dev.ashtonite365.apex.gui.hud.editor.ResizableHud
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.joml.Vector2i
import org.joml.Vector2ic

open class TestHud(var pos: Vector2i, val w: Int, val h: Int, var on: Boolean = true, val name: String = "x") : PlacedHud {
	override val hudId: Identifier = Identifier.fromNamespaceAndPath("t", name.lowercase())
	override val label: Component get() = Component.literal(name)
	override val isEnabled: Boolean get() = on
	override val unscaledWidth: Int get() = w
	override val unscaledHeight: Int get() = h
	override val topLeft: Vector2ic get() = pos
	override fun moveTo(x: Int, y: Int) { pos = Vector2i(x, y) }
}

open class TestScaledHud(pos: Vector2i, w: Int, h: Int, var sx: Float, var sy: Float = sx, on: Boolean = true, name: String = "x") :
	TestHud(pos, w, h, on, name), ResizableHud {
	override var uniformScale: Float
		get() = kotlin.math.sqrt(sx * sy)
		set(v) { sx = v; sy = v }
	override val scaleX: Float get() = sx
	override val scaleY: Float get() = sy
	override fun setScales(sx: Float, sy: Float) { this.sx = sx; this.sy = sy }
}
