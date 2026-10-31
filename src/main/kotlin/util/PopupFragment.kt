package dev.ashtonite365.apex.util

import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import me.shedaniel.math.Point
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

class PopupFragment(
	context: GuiContext,
	val position: Point,
	private val onDismissed: () -> Unit,
) : MoulConfigScreenComponent(Component.empty(), context, null) {

	private val rootWidth: Int get() = guiContext.root.width
	private val rootHeight: Int get() = guiContext.root.height

	override fun createContext(drawContext: GuiGraphicsExtractor?): GuiImmediateContext =
		super.createContext(drawContext).translated(position.x, position.y, rootWidth, rootHeight)

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		paintShifted(graphics)
	}

	private fun paintShifted(graphics: GuiGraphicsExtractor) {
		val pose = graphics.pose()
		pose.pushMatrix()
		pose.translate(position.x.toFloat(), position.y.toFloat())
		guiContext.root.render(createContext(graphics))
		pose.popMatrix()
	}

	override fun onClose() {
		onDismissed()
	}
}
