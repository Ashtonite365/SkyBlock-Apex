// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

object ValueProtection {
	@Config
	object TConfig : ManagedConfig("value-protection", Category.INVENTORY) {
		val enabled by toggle("enabled") { false }
		val thresholdThousands by integer("threshold", 10, 100_000) { 1000 }
		val protectDrop by toggle("protect-drop") { true }
		val protectSalvage by toggle("protect-salvage") { true }
		val protectNpcSell by toggle("protect-npc-sell") { true }
	}

	fun valueOf(stack: net.minecraft.world.item.ItemStack): Double {
		if (stack.isEmpty) return 0.0
		ItemValue.estimate(stack)?.let { return it.total * stack.count }
		val id = stack.view().repoItemId ?: return 0.0
		return (ItemValue.price(id) ?: 0.0) * stack.count
	}

	@Subscribe
	fun onProtect(event: IsSlotProtectedEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return
		val applies = when (event.origin) {
			IsSlotProtectedEvent.MoveOrigin.DROP_FROM_HOTBAR -> TConfig.protectDrop
			IsSlotProtectedEvent.MoveOrigin.SALVAGE -> TConfig.protectSalvage
			IsSlotProtectedEvent.MoveOrigin.INVENTORY_MOVE ->
				TConfig.protectNpcSell && SlotLocking.isNpcShop(Client.containerScreen) && isPlayerSlot(event.slot)
		}
		if (!applies) return
		val stack = runCatching { event.itemStack }.getOrNull() ?: return
		if (valueOf(stack) >= TConfig.thresholdThousands * 1000.0) event.protect()
	}

	private fun isPlayerSlot(slot: Slot?) = slot?.container is net.minecraft.world.entity.player.Inventory
}
