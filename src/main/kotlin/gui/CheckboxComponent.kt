package dev.ashtonite365.apex.gui

import com.mojang.blaze3d.platform.InputConstants
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import net.minecraft.client.renderer.RenderPipelines
import dev.ashtonite365.apex.Apex

class CheckboxComponent<T>(val state: GetSetter<T>, val value: T) : GuiComponent() {
	var isClicking = false

	override fun getWidth() = BOX_PX
	override fun getHeight() = BOX_PX

	fun isEnabled() = state.get() == value

	override fun render(context: GuiImmediateContext) {
		val sprite = if (isEnabled()) "widget/checkbox_checked" else "widget/checkbox_unchecked"
		(context.renderContext as MoulConfigRenderContext).drawContext
			.blitSprite(RenderPipelines.GUI_TEXTURED, Apex.identifier(sprite), 0, 0, BOX_PX, BOX_PX)
	}

	override fun mouseEvent(mouseEvent: MouseEvent, context: GuiImmediateContext): Boolean {
		val press = mouseEvent as? MouseEvent.Click ?: return false
		if (press.mouseButton != InputConstants.MOUSE_BUTTON_LEFT) return false
		return when {
			isClicking && !press.mouseState -> {
				disarm(context.isHovered)
				true
			}
			press.mouseState && context.isHovered -> {
				arm()
				true
			}
			else -> false
		}
	}

	private fun arm() = requestFocus().also { isClicking = true }

	private fun disarm(select: Boolean) {
		isClicking = false
		if (select) state.set(value)
		blur()
	}

	private companion object {
		const val BOX_PX = 16
	}
}
