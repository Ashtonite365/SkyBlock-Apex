// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.entity.Entity

data class EntityDespawnEvent(val entity: Entity?, val entityId: Int, val reason: Entity.RemovalReason) : ApexEvent() {
	companion object Companion : ApexEventBus<EntityDespawnEvent>()
}
