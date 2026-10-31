// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.hooks

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.events.HandledScreenClickEvent
import dev.ashtonite365.apex.events.HandledScreenKeyReleasedEvent
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.menus.MenuInputGuard
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.misc.SolidMenus
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers

object ContainerScreenHooks {

	private const val OUTSIDE = -999

	@JvmStatic
	fun released(screen: AbstractContainerScreen<*>, mouse: MouseButtonEvent): Boolean {
		val click = HandledScreenClickEvent(screen, mouse.x(), mouse.y(), mouse.button())
		val asKey = HandledScreenKeyReleasedEvent(screen, GenericInputAction.mouse(mouse), InputModifiers.of(mouse.modifiers()))
		if (MenuPress.press { HandledScreenClickEvent.publish(click).cancelled }) return true
		return HandledScreenKeyReleasedEvent.publish(asKey).cancelled
	}

	@JvmStatic
	fun dropSlotClick(screen: AbstractContainerScreen<*>, inventory: Inventory?, slot: Slot?, id: Int, button: Int, input: ContainerInput): Boolean {
		if (!MenuInputGuard.allowVanillaSlotClick(screen, id, input, button)) return true
		val origin = IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE
		val menu = screen.menu
		if (id == OUTSIDE && menu != null && input == ContainerInput.PICKUP &&
			IsSlotProtectedEvent.shouldBlockInteraction(slot, ContainerInput.THROW, origin, menu.carried)) return true
		if (IsSlotProtectedEvent.shouldBlockInteraction(slot, input, origin)) return true
		if (input != ContainerInput.SWAP || button !in 0..8) return false
		val playerInventory = inventory ?: return false
		return IsSlotProtectedEvent.shouldBlockInteraction(Slot(playerInventory, button, 0, 0), input, origin)
	}

	@JvmStatic
	fun drawSlot(g: GuiGraphicsExtractor, slot: Slot, vanilla: Runnable) {
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.SLOT_LISTENERS) { SlotRenderEvents.Before.publish(SlotRenderEvents.Before(g, slot)) }
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.ITEMS) { if (SolidMenus.shouldHide(slot)) SolidMenus.drawHidden(g, slot) else vanilla.run() }
		dev.ashtonite365.apex.features.debug.FrameProfiler.time(dev.ashtonite365.apex.features.debug.FrameProfiler.Phase.SLOT_LISTENERS) { SlotRenderEvents.After.publish(SlotRenderEvents.After(g, slot)) }
	}
}
