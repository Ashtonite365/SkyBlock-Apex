package dev.ashtonite365.apex.gui

import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import java.util.function.BiFunction
import java.util.function.Supplier
import kotlin.time.Duration
import dev.ashtonite365.apex.util.TimeMark

class ApexHoverComponent(val child: GuiComponent, val hoverLines: Supplier<List<String>>, val hoverDelay: Duration) : GuiComponent() {
	var permaHover = false
	var lastMouseMove = TimeMark.now()

	override fun getWidth() = child.width
	override fun getHeight() = child.height

	override fun <T : Any?> foldChildren(initial: T, visitor: BiFunction<GuiComponent, T, T>): T = visitor.apply(child, initial)

	override fun render(context: GuiImmediateContext) {
		val over = context.isHovered
		if (!over) permaHover = false
		val show = over && (permaHover || lastMouseMove.passedTime() >= hoverDelay)
		if (show) {
			permaHover = true
			val text = hoverLines.get().map(StructuredText::of)
			context.renderContext.scheduleDrawTooltip(context.mouseX, context.mouseY, text)
		}
		child.render(context)
	}

	override fun mouseEvent(mouseEvent: MouseEvent, context: GuiImmediateContext): Boolean =
		child.mouseEvent(mouseEvent, context).also { if (mouseEvent is MouseEvent.Move) lastMouseMove = TimeMark.now() }

	override fun keyboardEvent(event: KeyboardEvent, context: GuiImmediateContext) = child.keyboardEvent(event, context)
}
