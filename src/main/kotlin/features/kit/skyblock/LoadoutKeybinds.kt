// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.skyblock

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.SlotUtils.clickLeftMouseButton
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

object LoadoutKeybinds {
	@Config
	object TConfig : ManagedConfig("loadout-keybinds", Category.INVENTORY) {
		val enabled by riskyToggle("loadout-keybinds", RiskLevel.GREY_AREA)

		val nextPage by keyBindingWithDefaultUnbound("next-page")
		val previousPage by keyBindingWithDefaultUnbound("previous-page")
		val slotKeys = List(12) { keyBindingWithDefaultUnbound("loadout-${it + 1}") }

		override fun migrate(saved: kotlinx.serialization.json.JsonObject) =
			dev.ashtonite365.apex.features.inventory.UnbindWardrobeKeysMigration.migrate(name, saved)
		override fun extraSave() = dev.ashtonite365.apex.features.inventory.UnbindWardrobeKeysMigration.flag()
	}

	object Logic {

		val loadoutSlots = IntArray(12) { 14 + (it / 3) * 9 + it % 3 }
		private val PAGE_TITLE = Regex("\\((\\d+)/(\\d+)\\) Loadout")
		const val NEXT = 44
		const val PREVIOUS = 17

		fun page(title: String): Pair<Int, Int>? {
			val m = PAGE_TITLE.find(title) ?: return null
			val current = m.groupValues[1].toIntOrNull() ?: return null
			val total = m.groupValues[2].toIntOrNull() ?: return null
			return current to total
		}
	}

	private fun targetSlot(event: HandledScreenKeyPressedEvent, current: Int, total: Int): Int? {
		if (event.matches(TConfig.nextPage)) return if (current < total) Logic.NEXT else null
		if (event.matches(TConfig.previousPage)) return if (current > 1) Logic.PREVIOUS else null
		for ((i, key) in TConfig.slotKeys.withIndex()) if (event.matches(key.get())) return Logic.loadoutSlots[i]
		return null
	}

	@Subscribe
	fun onKey(event: HandledScreenKeyPressedEvent) {
		if (!TConfig.enabled || event.isRepeat) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val (current, total) = Logic.page(screen.title.string) ?: return
		val slot = targetSlot(event, current, total) ?: return
		event.cancel()
		screen.menu.slots.getOrNull(slot)?.clickLeftMouseButton(screen.menu)
	}
}
