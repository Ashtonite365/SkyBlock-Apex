package dev.ashtonite365.apex.util

import com.google.auto.service.AutoService
import dev.ashtonite365.apex.api.v1.ApexAPI
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.util.compatloader.CompatLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.item.ItemStack

interface HoverStackSource : Comparable<HoverStackSource> {
	fun stackUnderMouse(screen: Screen): ItemStack?

	val sourcePriority: Int get() = 0

	override fun compareTo(other: HoverStackSource): Int = sourcePriority.compareTo(other.sourcePriority)

	companion object : CompatLoader<HoverStackSource>(HoverStackSource::class) {
		val sorted: List<HoverStackSource> = allValidInstances.sorted()
	}
}

@AutoService(HoverStackSource::class)
class VanillaHoverSource : HoverStackSource {
	override val sourcePriority: Int get() = -1

	override fun stackUnderMouse(screen: Screen): ItemStack? =
		(screen as? AccessorHandledScreen)?.focusedSlot_Apex?.item
}

@AutoService(HoverStackSource::class)
class ApexStackScreenProvider : HoverStackSource {
	override fun stackUnderMouse(screen: Screen): ItemStack? {
		val widget = ApexAPI.getInstance().hoveredItemWidget
		return if (widget.isPresent) widget.get().itemStack else null
	}
}

val Screen.stackUnderCursor: ItemStack?
	get() {
		for (provider in HoverStackSource.sorted) {
			val stack = provider.stackUnderMouse(this)
			if (stack != null && !stack.isEmpty) return stack
		}
		return null
	}
