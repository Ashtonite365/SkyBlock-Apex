package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import me.shedaniel.math.Color
import net.minecraft.world.item.ItemStack

data class DurabilityBarEvent(val item: ItemStack) : ApexEvent() {

	data class DurabilityBar(val color: Color, val percentage: Float)

	var barOverride: DurabilityBar? = null

	companion object : ApexEventBus<DurabilityBarEvent>()
}
