// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.skyblock

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.SlotUtils.clickLeftMouseButton
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

object PetKeybinds {
	@Config
	object TConfig : ManagedConfig("pet-keybinds", Category.INVENTORY) {
		val enabled by riskyToggle("pet-keybinds", RiskLevel.GREY_AREA)
		val unequip by keyBindingWithDefaultUnbound("unequip")
		val nextPage by keyBindingWithDefaultUnbound("next-page")
		val previousPage by keyBindingWithDefaultUnbound("previous-page")
		val noUnequip by toggle("disable-unequip") { false }
		val closeIfEquipped by toggle("close-if-equipped") { false }
		val petKeys = List(9) { keyBindingWithDefaultUnbound("pet-${it + 1}") }
	}

	object Logic {

		val petSlots = intArrayOf(10, 11, 12, 13, 14, 15, 16, 19, 20)
		private val TITLE = Regex("(?:\\((\\d+)/(\\d+)\\)\\s*)?Pets")
		const val NEXT = 53
		const val PREVIOUS = 45
		const val CLOSE = 49

		fun page(title: String): Pair<Int, Int>? {
			val m = TITLE.matchEntire(title) ?: return null
			val current = m.groupValues[1].toIntOrNull() ?: 1
			val total = m.groupValues[2].toIntOrNull() ?: 1
			return current to total
		}
	}

	private const val DESPAWN_HINT = "Click to despawn!"

	@Subscribe
	fun onKey(event: HandledScreenKeyPressedEvent) {
		if (!TConfig.enabled || event.isRepeat) return
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val (current, total) = Logic.page(screen.title.string) ?: return
		val slots = screen.menu.slots
		val isEquipped = { i: Int ->
			slots.getOrNull(i)?.item?.view()?.loreAccordingToNbt?.any { it.bareText.contains(DESPAWN_HINT) } == true
		}
		val unequipKey = event.matches(TConfig.unequip)
		var target: Int
		if (event.matches(TConfig.nextPage)) {
			if (current >= total) return
			target = Logic.NEXT
		} else if (event.matches(TConfig.previousPage)) {
			if (current <= 1) return
			target = Logic.PREVIOUS
		} else if (unequipKey) {
			var found = -1
			for (i in 10 until minOf(43, slots.size)) if (isEquipped(i)) { found = i; break }
			if (found < 0) return Client.showChat(Component.literal("§cCouldn't find equipped pet"))
			target = found
		} else {
			val k = TConfig.petKeys.indexOfFirst { event.matches(it.get()) }
			if (k < 0) return
			target = Logic.petSlots[k]
		}
		if (!event.matches(TConfig.unequip) && isEquipped(target)) {
			Client.showChat(Component.literal("§cThat pet is already equipped!"))
			if (TConfig.closeIfEquipped) target = Logic.CLOSE
			else if (TConfig.noUnequip) return
		}
		event.cancel()
		slots.getOrNull(target)?.clickLeftMouseButton(screen.menu)
	}
}
