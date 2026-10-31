// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.kit

import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.EntityUpdateEvent
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.features.plus.rift.PlusBlockChangeEvent

typealias BlockChangeEvent = PlusBlockChangeEvent

data class EntityMetadataEvent(val entity: Entity, val values: List<SynchedEntityData.DataValue<*>>) : ApexEvent() {
	val entityId: Int get() = entity.id

	companion object : ApexEventBus<EntityMetadataEvent>() {
		@Subscribe
		fun onUpdate(event: EntityUpdateEvent) {
			val changed = event as? EntityUpdateEvent.TrackedDataUpdate ?: return
			publish(EntityMetadataEvent(changed.entity, changed.dataValues))
		}
	}
}
