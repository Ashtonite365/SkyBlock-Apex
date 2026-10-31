package dev.ashtonite365.apex.features.inventory.storageoverlay

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt

object StorageBackChain {
	data class Item(val name: String?, val lore: List<String> = emptyList())

	fun findBack(items: List<Item?>): Int? {
		val byName = StorageNavButtons.resolve(items.map { it?.name })[StorageNavButtons.Nav.BACK]
		if (byName != null) return byName
		return items.indexOfFirst { it?.name != null && "back" in StorageNavButtons.normalise(it.name).split(' ') &&
			it.lore.any { l -> StorageNavButtons.normalise(l).startsWith("to ") } }.takeIf { it >= 0 }
	}

	fun onBack(items: List<Item?>, click: (Int) -> Unit): Boolean {
		val slot = findBack(items) ?: return false
		click(slot)
		return true
	}

	fun items(menu: AbstractContainerMenu): List<Item?> = menu.slots.map { slot ->
		if (slot.container is Inventory || !slot.hasItem()) null
		else Item(net.minecraft.ChatFormatting.stripFormatting(slot.item.hoverName.string),
			slot.item.view().loreAccordingToNbt.map { it.string })
	}
}
