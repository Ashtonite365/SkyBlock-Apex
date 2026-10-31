package dev.ashtonite365.apex.gui

import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import java.util.function.BiFunction

class FixedComponent(val fixedWidth: GetSetter<Int>?, val fixedHeight: GetSetter<Int>?, val component: GuiComponent) : GuiComponent() {
	private fun dimension(fixed: GetSetter<Int>?, natural: () -> Int) = fixed?.get() ?: natural()

	override fun getWidth() = dimension(fixedWidth) { component.width }
	override fun getHeight() = dimension(fixedHeight) { component.height }

	override fun <T : Any?> foldChildren(initial: T, visitor: BiFunction<GuiComponent, T, T>): T = visitor.apply(component, initial)

	fun fixContext(context: GuiImmediateContext): GuiImmediateContext = context.translated(0, 0, width, height)

	override fun render(context: GuiImmediateContext) = component.render(fixContext(context))
	override fun mouseEvent(mouseEvent: MouseEvent, context: GuiImmediateContext) = component.mouseEvent(mouseEvent, fixContext(context))
	override fun keyboardEvent(event: KeyboardEvent, context: GuiImmediateContext) = component.keyboardEvent(event, fixContext(context))
}
