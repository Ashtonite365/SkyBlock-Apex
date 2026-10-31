// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.customgui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.events.HandledScreenKeyReleasedEvent
import dev.ashtonite365.apex.features.inventory.SlotLinkOverlay
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.util.ItemListInputBridge

object CustomGuiHost {

	private fun itemListTyping() = ItemListInputBridge.itemListHasFocus()

	@JvmStatic
	fun drawFront(screen: Screen, gui: CustomGui, g: GuiGraphicsExtractor, mx: Int, my: Int, partial: Float) {
		gui.render(g, partial, mx, my)

		SlotLinkOverlay.afterCustomGui(screen, gui, g, mx, my)
	}

	@JvmStatic
	fun keyUp(screen: Screen, gui: CustomGui?, key: KeyEvent): Boolean {
		val event = HandledScreenKeyReleasedEvent(screen, GenericInputAction.of(key), InputModifiers.of(key))
		if (HandledScreenKeyReleasedEvent.publish(event).cancelled) return true
		return gui?.keyReleased(key) ?: false
	}

	@JvmStatic
	fun keyDown(gui: CustomGui?, key: KeyEvent): Boolean? =
		if (gui == null || itemListTyping()) null else gui.keyPressed(key)

	@JvmStatic
	fun typed(gui: CustomGui?, ch: CharacterEvent): Boolean =
		gui != null && !itemListTyping() && gui.charTyped(ch)

	@JvmStatic
	fun press(gui: CustomGui?, click: MouseButtonEvent, doubled: Boolean): Boolean =
		gui != null && MenuPress.press { gui.mouseClick(click, doubled) }

	@JvmStatic
	fun aroundSlot(gui: CustomGui?, g: GuiGraphicsExtractor, slot: Slot, draw: Runnable) {
		gui?.beforeSlotRender(g, slot)
		draw.run()
		gui?.afterSlotRender(g, slot)
	}

	@JvmStatic
	fun aroundHighlight(gui: CustomGui?, g: GuiGraphicsExtractor, hovered: Slot?, draw: Runnable) {
		gui?.beforeSlotHighlight(g, hovered)
		draw.run()
		gui?.afterSlotHighlight(g, hovered)
	}

	@JvmStatic
	fun layoutSlots(gui: CustomGui?, slots: List<Slot>, saved: Boolean, left: Int, top: Int): Boolean {
		if (gui == null) {
			if (saved) slots.forEach { (it as CoordRememberingSlot).restoreCoords_apex() }
			return false
		}
		for (slot in slots) {
			if (!saved) (slot as CoordRememberingSlot).rememberCoords_apex()
			gui.moveSlot(slot, left, top)
		}
		return true
	}
}
