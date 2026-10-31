// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.minion

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.IsSlotProtectedEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.features.minions.MinionHelper.TConfig
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object MinionPlusPorts {
	const val FUEL_SLOT = 19
	const val PICKUP_SLOT = 53
	private val INFERNO_TITLE = Regex("^Inferno Minion (?:[IVX]{1,4}|\\d+)$")
	private val FUEL_NAMES = listOf("Inferno Minion Fuel", "Inferno Fuel")

	fun isInfernoTitle(title: String?): Boolean = title?.let(INFERNO_TITLE::matches) ?: false
	fun isInfernoFuel(name: String): Boolean = FUEL_NAMES.any(name::startsWith)

	private fun fuelLoaded(): Boolean {
		if (!TConfig.infernoFuelBlocker || !isInfernoTitle(Client.screenTitle)) return false
		val slots = Client.player?.containerMenu?.slots ?: return false
		val fuel = slots.getOrNull(FUEL_SLOT)?.item ?: return false
		if (fuel.isEmpty) return false
		return isInfernoFuel(fuel.hoverName.bareText)
	}

	@Subscribe
	fun onProtect(event: IsSlotProtectedEvent) {
		val slot = event.slot ?: return
		if (slot.index !in setOf(FUEL_SLOT, PICKUP_SLOT) || slot.container == Client.player?.inventory) return
		if (SavedKeyBinding.isShiftDown() || !fuelLoaded()) return
		event.protect()
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!fuelLoaded()) return
		val name = event.stack.hoverName.bareText
		if (!isInfernoFuel(name) && name != "Pickup Minion") return
		event.lines.add(Component.literal("§c[SkyBlock Apex] Blocked: Inferno fuel inside. §7(hold Shift to bypass)"))
	}

	private val NEED_MORE = Regex("^\\s*You need (\\d+) more (.+)\\.$")
	private val ANY_MINION_TITLE = Regex(".* Minion (?:[IVX]{1,4}|\\d+)$")

	fun upgradeNeed(lore: List<String>): Pair<String, Int>? {
		for (line in lore) {
			val g = NEED_MORE.matchEntire(line)?.groupValues ?: continue
			return g[2] to g[1].toInt()
		}
		return null
	}

	@Subscribe
	fun onUpgradeTooltip(event: ItemTooltipEvent) {
		if (!TConfig.upgradeHelper) return
		val title = Client.screenTitle ?: return
		if (!ANY_MINION_TITLE.matches(title)) return
		val (item, amount) = upgradeNeed(event.lines.map { it.bareText }) ?: return
		event.lines.add(Component.literal("§b[SkyBlock Apex] §7Get them: §e/gfs $item $amount"))
	}
}
