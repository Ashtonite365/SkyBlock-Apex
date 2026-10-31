// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.features.inventory.SlotLocking
import dev.ashtonite365.apex.util.CommonSoundEffects
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.hover
import dev.ashtonite365.apex.util.red
import dev.ashtonite365.apex.util.tr

data class IsSlotProtectedEvent(val slot: Slot?, val actionType: ContainerInput, var isProtected: Boolean,
		val itemStackOverride: ItemStack?, val origin: MoveOrigin, var silent: Boolean = false) : ApexEvent() {

	val itemStack: ItemStack get() = itemStackOverride ?: slot!!.item

	fun protect() = markProtected(quietly = false)

	fun protectSilent() = markProtected(quietly = true)

	private fun markProtected(quietly: Boolean) {
		if (isProtected) return
		silent = quietly
		this.isProtected = true
	}

	enum class MoveOrigin { DROP_FROM_HOTBAR, SALVAGE, INVENTORY_MOVE }

	companion object Companion : ApexEventBus<IsSlotProtectedEvent>() {

		@JvmStatic
		@JvmOverloads
		fun shouldBlockInteraction(slot: Slot?, action: ContainerInput, origin: MoveOrigin, itemStackOverride: ItemStack? = null): Boolean {
			if (itemStackOverride == null && slot == null) return false
			val query = publish(IsSlotProtectedEvent(slot, action, false, itemStackOverride, origin))
			if (query.isProtected && !query.silent) notifyBlocked(query)
			return query.isProtected
		}

		private fun notifyBlocked(query: IsSlotProtectedEvent) {
			if (SlotLocking.TConfig.protectMessage) {
				val hint = tr("apex.protectitem.hint",
					"To unlock this item use the Lock Slot or Lock Item keybind from SkyBlock Apex while hovering over this item. If this is a bound slot, you can use disable the Lock Bound Slots setting.")
				val headline = tr("apex.protectitem", "SkyBlock Apex protected your item: ${query.itemStack.hoverName}.\n").red()
				Client.showChat(headline.append(tr("apex.protectitem.hoverhint", "Hover for more info.").grey()).hover(hint))
			}
			if (SlotLocking.TConfig.protectSound) CommonSoundEffects.playFailure()
		}
	}
}
