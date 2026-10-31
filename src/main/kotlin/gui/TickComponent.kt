package dev.ashtonite365.apex.gui

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext

class TickComponent(val onTick: Runnable) : GuiComponent() {
	override fun getWidth() = 0
	override fun getHeight() = 0
	override fun render(context: GuiImmediateContext) = onTick.run()
}
