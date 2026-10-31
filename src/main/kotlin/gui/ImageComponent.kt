package dev.ashtonite365.apex.gui

import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import java.util.function.Supplier

class ImageComponent(width: Int, height: Int, val Identifier: Supplier<MyResourceLocation>, val u1: Float, val u2: Float, val v1: Float, val v2: Float) :
	GuiComponent() {
	private val size = width to height

	override fun getWidth() = size.first
	override fun getHeight() = size.second

	override fun render(context: GuiImmediateContext) {
		val (w, h) = context.width.toFloat() to context.height.toFloat()
		context.renderContext.drawComplexTexture(Identifier.get(), 0F, 0F, w, h) { tex -> tex.uv(u1, v1, u2, v2) }
	}
}
