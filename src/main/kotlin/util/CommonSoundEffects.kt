package dev.ashtonite365.apex.util

import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

object CommonSoundEffects {
	private val anvilLand = Identifier.withDefaultNamespace("block.anvil.place")
	private val arrowDing = Identifier.withDefaultNamespace("entity.arrow.hit_player")

	fun playSound(identifier: Identifier) {
		val event = SoundEvent.createVariableRangeEvent(identifier)
		Client.sounds.play(SimpleSoundInstance.forUI(event, 1F))
	}

	fun playFailure() = playSound(anvilLand)

	fun playSuccess() = playDing()

	fun playDing() = playSound(arrowDing)
}
