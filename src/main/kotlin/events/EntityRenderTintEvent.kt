// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import dev.ashtonite365.apex.util.render.TintOverlay

class EntityRenderTintEvent(val entity: Entity, val renderState: HasTintRenderState) : ApexEvent.Cancellable() {
	init {
		cancelled = entity !is LivingEntity
	}

	@Suppress("PropertyName", "FunctionName")
	interface HasTintRenderState {

		var tint_apex: Int
		var hasTintOverride_apex: Boolean

		var overlayTexture_apex: TintOverlay?

		fun reset_apex()

		companion object Companion {
			@JvmStatic
			fun cast(state: EntityRenderState): HasTintRenderState = state as HasTintRenderState
		}
	}

	companion object Companion : ApexEventBus<EntityRenderTintEvent>() {

		@JvmField
		var overlayOverride: OverlayTexture? = null
	}
}
