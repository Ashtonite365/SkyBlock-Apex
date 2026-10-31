// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object DungeonLootMarks {
	@Config
	object TConfig : ManagedConfig("dungeon-loot-highlighter", Category.DUNGEONS) {
		val salvageHelper by toggle("salvage-helper") { true }
		val salvageMaxPrice by integer("salvage-max-price", 0, 1_000_000) { 100_000 }
		val sellableHighlighter by toggle("sellable-highlighter") { true }
	}

	private const val MARK = 0x80FFFF00.toInt()
	private const val GEAR_TAG = "DUNGEON"
	private val junkIds = setOf("DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE")
	private const val JUNK_POTION = "Healing VIII Splash Potion"

	fun isGearRarityLine(line: String): Boolean {
		var at = line.indexOf(GEAR_TAG)
		while (at >= 0) {
			if (!line.startsWith(" ITEM", at + GEAR_TAG.length)) return true
			at = line.indexOf(GEAR_TAG, at + 1)
		}
		return false
	}

	fun cheapEnough(price: Double?, limit: Int): Boolean = price != null && price > 0 && price < limit

	fun isJunk(coreId: String, itemName: String): Boolean = isJunk(coreId) { itemName }

	private inline fun isJunk(coreId: String, itemName: () -> String): Boolean =
		if (coreId == "POTION" || coreId.startsWith("POTION_")) JUNK_POTION in itemName() else coreId in junkIds

	private fun salvageMark(stack: ItemStack): Boolean {
		val data = stack.view()
		if (data.loreAccordingToNbt.none { isGearRarityLine(it.bareText) }) return false
		val id = data.repoItemId ?: return false
		return cheapEnough(DungeonChestProfit.priceOf(id), TConfig.salvageMaxPrice)
	}

	fun isJunkItem(stack: ItemStack): Boolean {
		val id = stack.view().repoItemId ?: return false
		return isJunk(id.skyblockId) { stack.hoverName.bareText }
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		val menu = Client.screen as? AbstractContainerScreen<*> ?: return
		val stack = event.slot.item
		if (stack.isEmpty) return
		val title = menu.title.bareText.trim()
		val marked = if (title.startsWith("Salvage Items")) TConfig.salvageHelper && salvageMark(stack)
		else if (title == "Ophelia" || title == "Booster Cookie") TConfig.sellableHighlighter && event.slot.container is Inventory && isJunkItem(stack)
		else false
		if (marked) event.context.fill(event.slot.x, event.slot.y, event.slot.x + 16, event.slot.y + 16, MARK)
	}
}
