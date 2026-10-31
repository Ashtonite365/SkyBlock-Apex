package dev.ashtonite365.apex.util.render

import me.shedaniel.math.Rectangle
import org.joml.Matrix3x2f
import org.joml.Vector2f
import net.minecraft.client.gui.GuiGraphicsExtractor

fun GuiGraphicsExtractor.enableScissorWithTranslation(area: Rectangle) =
	enableScissor(area.minX, area.minY, area.maxX, area.maxY)

fun GuiGraphicsExtractor.enableScissorWithTranslation(ax: Float, ay: Float, bx: Float, by: Float) =
	enableScissor(ax.toInt(), ay.toInt(), bx.toInt(), by.toInt())

fun GuiGraphicsExtractor.enableScissorWithoutTranslation(ax: Float, ay: Float, bx: Float, by: Float) {
	val undo = Matrix3x2f(pose()).invert()
	val a = undo.transformPosition(Vector2f(ax, ay))
	val b = undo.transformPosition(Vector2f(bx, by))
	enableScissorWithTranslation(a.x, a.y, b.x, b.y)
}
