// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.customgui

import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay
import dev.ashtonite365.apex.util.TextInputFocus

abstract class CustomGui : TextInputFocus {

	abstract fun getBounds(): List<Rectangle>

	open fun sidePanelAnchor(): Rectangle? = null

	override val textInputFocused: Boolean get() = false

	open fun render(drawContext: GuiGraphicsExtractor, delta: Float, mouseX: Int, mouseY: Int) = Unit

	open fun beforeSlotRender(context: GuiGraphicsExtractor, slot: Slot) = Unit
	open fun afterSlotRender(context: GuiGraphicsExtractor, slot: Slot) = Unit

	open fun beforeSlotHighlight(context: GuiGraphicsExtractor, slot: Slot?) = Unit
	open fun afterSlotHighlight(context: GuiGraphicsExtractor, slot: Slot?) = Unit

	open fun shouldDrawForeground(): Boolean = true

	open fun moveSlot(slot: Slot) = Unit

	open fun moveSlot(slot: Slot, leftPos: Int, topPos: Int) = moveSlot(slot)

	open fun slotLinkBoxes(screen: AbstractContainerScreen<*>): Map<Int, SlotLinkOverlay.SlotBox> = SlotLinkOverlay.realSlotBoxes(screen)

	private fun inAnyPanel(px: Double, py: Double) = getBounds().any { panel -> panel.contains(px, py) }

	open fun isClickOutsideBounds(mouseX: Double, mouseY: Double): Boolean = !inAnyPanel(mouseX, mouseY)

	open fun isPointWithinBounds(x: Int, y: Int, width: Int, height: Int, pointX: Double, pointY: Double): Boolean =
		inAnyPanel(pointX, pointY) && Rectangle(x, y, width, height).contains(pointX, pointY)

	open fun isPointOverSlot(slot: Slot, xOffset: Int, yOffset: Int, pointX: Double, pointY: Double): Boolean =
		isPointWithinBounds(xOffset + slot.x, yOffset + slot.y, SLOT_PX, SLOT_PX, pointX, pointY)

	open fun mouseClick(click: MouseButtonEvent, doubled: Boolean): Boolean = false
	open fun mouseReleased(click: MouseButtonEvent): Boolean = false
	open fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean = false
	open fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean = false
	open fun keyPressed(input: KeyEvent): Boolean = false
	open fun keyReleased(input: KeyEvent): Boolean = false
	open fun charTyped(input: CharacterEvent): Boolean = false

	open fun onInit() = Unit

	open fun onVoluntaryExit(): Boolean = true

	companion object {
		private const val SLOT_PX = 16

		@Subscribe
		fun onExclusionZone(event: HandledScreenPushREIEvent) {
			event.screen.customGui?.getBounds()?.let(event.rectangles::addAll)
		}
	}
}
