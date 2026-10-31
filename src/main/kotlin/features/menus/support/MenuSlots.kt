// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object MenuSlots {

	fun snapshot(screen: AbstractContainerScreen<*>): Pair<List<SlotText>, Map<Int, ItemStack>> {
		val texts = mutableListOf<SlotText>()
		val stacks = mutableMapOf<Int, ItemStack>()
		for (slot in screen.menu.slots) {
			if (slot.container is Inventory) continue
			val st = slot.item
			if (st.isEmpty) continue
			texts += SlotText(slot.index, st.hoverName.bareText, st.view().loreAccordingToNbt.map { it.bareText })
			stacks[slot.index] = st
		}
		return texts to stacks
	}

	fun signature(screen: AbstractContainerScreen<*>): Int {
		var h = screen.menu.stateId * 31 + screen.menu.containerId
		for (slot in screen.menu.slots) if (slot.container !is Inventory) h = h * 31 + slot.item.hashCode() * 7 + slot.item.count
		return h
	}
}
