// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.kit

import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus

data class EntityInteractCancellableEvent(val entity: Entity, val hand: InteractionHand) : ApexEvent.Cancellable() {
	companion object : ApexEventBus<EntityInteractCancellableEvent>()
}
