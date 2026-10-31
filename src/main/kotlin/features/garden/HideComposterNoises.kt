// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import net.minecraft.world.entity.animal.wolf.WolfSoundVariants
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SoundReceiveEvent
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object HideComposterNoises {
	@Config
	object TConfig : ManagedConfig("composter", Category.GARDEN) { val hideComposterNoises by toggle("no-more-noises") { false } }

	val composterSoundEvents: List<SoundEvent> = buildList {
		add(SoundEvents.PISTON_EXTEND); add(SoundEvents.WATER_AMBIENT); add(SoundEvents.CHICKEN_EGG)
		add(SoundEvents.WOLF_SOUNDS.getValue(WolfSoundVariants.SoundSet.CLASSIC).adultSounds.growlSound.value())
	}

	private fun muting() = TConfig.hideComposterNoises && SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN

	@Subscribe
	fun muteComposter(event: SoundReceiveEvent) {
		if (muting() && composterSoundEvents.contains(event.sound.value())) event.cancel()
	}
}
