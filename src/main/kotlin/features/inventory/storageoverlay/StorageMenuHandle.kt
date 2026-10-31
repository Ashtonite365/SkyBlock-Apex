package dev.ashtonite365.apex.features.inventory.storageoverlay

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.ChestMenu
import dev.ashtonite365.apex.util.bareText

sealed interface StorageMenuHandle {

	sealed interface OpenMenu {
		val handler: ChestMenu
	}

	data class Overview(override val handler: ChestMenu) : StorageMenuHandle, OpenMenu

	data class Page(override val handler: ChestMenu, val storagePageSlot: StoragePageSlot) : StorageMenuHandle, OpenMenu

	companion object {
		private const val OVERVIEW_TITLE = "Storage"
		private val enderChestTitle = Regex("Ender Chest (?:✦ )?\\(([1-9])/[1-9]\\)")
		private val backpackTitle = Regex(".+Backpack (?:✦ )?\\(Slot #([0-9]+)\\)")

		fun isOverviewTitle(title: String): Boolean = title == OVERVIEW_TITLE

		fun pageOfTitle(title: String): StoragePageSlot? {
			enderChestTitle.matchEntire(title)?.let { return StoragePageSlot.ofEnderChestPage(it.groupValues[1].toInt()) }
			val backpack = backpackTitle.matchEntire(title)?.groupValues?.get(1)?.toIntOrNull() ?: return null
			return if (backpack in 1..18) StoragePageSlot.ofBackPackPage(backpack) else null
		}

		fun fromScreen(screen: Screen?): StorageMenuHandle? {
			val chest = (screen as? ContainerScreen)?.menu ?: return null
			val title = screen.title.bareText
			if (isOverviewTitle(title)) return Overview(chest)
			return pageOfTitle(title)?.let { Page(chest, it) }
		}
	}
}
