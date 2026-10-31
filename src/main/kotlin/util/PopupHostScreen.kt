package dev.ashtonite365.apex.util

import io.github.notenoughupdates.moulconfig.gui.GuiContext
import me.shedaniel.math.Point
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

abstract class PopupHostScreen(
	val dismissOnOutOfBounds: Boolean = true,
) : Screen(Component.literal("")), TextInputFocus {
	var popup: PopupFragment? = null

	override val textInputFocused get() = popup != null

	fun openPopup(context: GuiContext, position: Point) {
		popup = PopupFragment(context, position) { popup = null }
	}

	fun paintPopup(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		popup?.extractRenderState(graphics, mouseX, mouseY, partialTick)
	}

	private inline fun <R> routed(toPopup: (PopupFragment) -> Any?, consumed: R, own: () -> R): R {
		val open = popup ?: return own()
		toPopup(open)
		return consumed
	}

	private fun PopupFragment.covers(x: Double, y: Double): Boolean {
		val root = guiContext.root
		return x >= position.x && y >= position.y && x < position.x + root.width && y < position.y + root.height
	}

	override fun keyPressed(input: KeyEvent): Boolean = routed({ it.keyPressed(input) }, true) { super.keyPressed(input) }

	override fun keyReleased(input: KeyEvent): Boolean = routed({ it.keyReleased(input) }, true) { super.keyReleased(input) }

	override fun charTyped(input: CharacterEvent): Boolean = routed({ it.charTyped(input) }, true) { super.charTyped(input) }

	override fun mouseMoved(x: Double, y: Double) = routed({ it.mouseMoved(x, y) }, Unit) { super.mouseMoved(x, y) }

	override fun mouseReleased(click: MouseButtonEvent): Boolean =
		routed({ it.mouseReleased(click) }, true) { super.mouseReleased(click) }

	override fun mouseDragged(click: MouseButtonEvent, moveX: Double, moveY: Double): Boolean =
		routed({ it.mouseDragged(click, moveX, moveY) }, true) { super.mouseDragged(click, moveX, moveY) }

	override fun mouseScrolled(x: Double, y: Double, sideways: Double, wheel: Double): Boolean =
		routed({ it.mouseScrolled(x, y, sideways, wheel) }, true) { super.mouseScrolled(x, y, sideways, wheel) }

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val open = popup ?: return super.mouseClicked(click, doubled)
		return when {
			open.covers(click.x(), click.y()) -> { open.mouseClicked(click, doubled); true }
			dismissOnOutOfBounds -> { popup = null; true }
			else -> super.mouseClicked(click, doubled)
		}
	}
}
