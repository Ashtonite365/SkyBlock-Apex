// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.core.Holder
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.phys.Vec3

data class SoundReceiveEvent(val sound: Holder<SoundEvent>, val category: SoundSource, val position: Vec3,
		val pitch: Float, val volume: Float, val seed: Long) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<SoundReceiveEvent>()
}
