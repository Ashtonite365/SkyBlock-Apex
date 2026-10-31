// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import com.mojang.datafixers.util.Pair
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.item.ItemStack
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.Client

sealed class EntityUpdateEvent : ApexEvent() {

	abstract val entity: Entity

	data class AttributeUpdate(override val entity: LivingEntity, val snapshots: List<ClientboundUpdateAttributesPacket.AttributeSnapshot>) :
		EntityUpdateEvent()

	data class TrackedDataUpdate(override val entity: Entity, val dataValues: List<SynchedEntityData.DataValue<*>>) :
		EntityUpdateEvent()

	data class EquipmentUpdate(override val entity: Entity, val changedSlots: List<Pair<EquipmentSlot, ItemStack>>) :
		EntityUpdateEvent()

	companion object Companion : ApexEventBus<EntityUpdateEvent>() {

		private const val FIRST_ARMOUR_INDEX = 36
		private const val OFFHAND_INDEX = 40

		@Subscribe
		fun onPlayerInventoryUpdate(event: PlayerInventoryUpdate) {
			val player = Client.player ?: return
			val feetUp = listOf(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)
			val watched = feetUp.mapIndexed { i, slot -> slot to FIRST_ARMOUR_INDEX + i } + (EquipmentSlot.OFFHAND to OFFHAND_INDEX)
			val changed = watched.mapNotNull { (slot, inventoryIndex) ->

				val menuSlot = if (inventoryIndex == OFFHAND_INDEX) 45 else 8 - (inventoryIndex - FIRST_ARMOUR_INDEX)
				val stack = event.getOrNull(menuSlot) ?: return@mapNotNull null
				Pair.of(slot, stack)
			}
			if (changed.isNotEmpty()) publish(EquipmentUpdate(player, changed))
		}
	}
}
