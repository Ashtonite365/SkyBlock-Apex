// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.state.level.CameraRenderState

data class WorldRenderLastEvent(val matrices: PoseStack, val camera: CameraRenderState) : ApexEvent() {
	companion object Companion : ApexEventBus<WorldRenderLastEvent>()
}
