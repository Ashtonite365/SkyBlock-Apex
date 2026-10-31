// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.events

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.util.render.guiSprite

interface SlotRenderEvents {

	val slot: Slot

	val context: GuiGraphicsExtractor

	fun highlight(sprite: Identifier) = context.guiSprite(sprite, slot.x, slot.y, SLOT_PIXELS, SLOT_PIXELS)

	data class Before(override val context: GuiGraphicsExtractor, override val slot: Slot) : SlotRenderEvents, ApexEvent() {
		companion object : ApexEventBus<Before>()
	}

	data class After(override val context: GuiGraphicsExtractor, override val slot: Slot) : SlotRenderEvents, ApexEvent() {
		companion object : ApexEventBus<After>()
	}

	private companion object {
		const val SLOT_PIXELS = 16
	}
}
