// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.entity.Entity
import net.minecraft.world.InteractionHand

data class EntityInteractionEvent(val kind: InteractionKind, val entity: Entity, val hand: InteractionHand) : ApexEvent() {
	companion object Companion : ApexEventBus<EntityInteractionEvent>()

	enum class InteractionKind { ATTACK, INTERACT }
}
