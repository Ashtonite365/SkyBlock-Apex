/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): HOTM perk cost tooltip. Uses the perk cost formulas from the item repo
 * (constants/hotmlayout.json; see CREDITS.md) with the usual variable
 * bindings (level = current level, at least 1; level0 = current level; maxLevel). Evaluated with TinyLisp.
 */
package dev.ashtonite365.apex.features.mining

import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.math.TinyLisp
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

object HotmCostTooltip {
	data class Perk(val name: String, val maxLevel: Int, val powder: String, val cost: String)

	fun perks(layout: JsonObject?): Map<String, Perk> =
		layout?.getAsJsonObject("hotm")?.getAsJsonObject("perks")?.entrySet()?.mapNotNull { (_, el) ->
			val o = el.asJsonObject
			val cost = o.get("cost")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
			Perk(o.get("name").asString, o.get("maxLevel").asInt, o.get("powder")?.asString ?: "", cost)
		}?.associateBy { it.name } ?: emptyMap()

	fun prelude(layout: JsonObject?): List<String> = layout?.getAsJsonArray("prelude")?.map { it.asString } ?: emptyList()

	fun costAt(prelude: List<String>, perk: Perk, level: Int, potm: Int = 0): Long? {
		val v = TinyLisp.evaluate(prelude, perk.cost, mapOf(
			"level" to (if (level == 0) 1.0 else level.toDouble()), "level0" to level.toDouble(),
			"maxLevel" to perk.maxLevel.toDouble(), "potm" to potm.toDouble()))
		return (v as? Double)?.let { kotlin.math.floor(it).toLong() }
	}

	fun costToMax(prelude: List<String>, perk: Perk, level: Int): Long? =
		(level until perk.maxLevel).sumOf { costAt(prelude, perk, it) ?: return null }

	private val LEVEL = Regex("^Level (\\d+)(?:/\\d+)?$")

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!MiningSolvers.TConfig.hotmCosts || Client.screenTitle?.startsWith("Heart of the Mountain") != true) return
		val layout = RepoConstants.get("hotmlayout") ?: return
		val perk = perks(layout)[event.stack.hoverName.bareText.trim()] ?: return
		val lore = event.stack.view().loreAccordingToNbt.map { it.bareText.trim() }
		val level = lore.firstNotNullOfOrNull { LEVEL.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
		if (level >= perk.maxLevel) return
		val pre = prelude(layout)
		val powder = perk.powder.lowercase().replaceFirstChar(Char::uppercase)
		costAt(pre, perk, level)?.let { event.lines.add(Component.literal("§7Next level: §b${ApexFormatters.withThousands(it)} $powder Powder")) }
		if (dev.ashtonite365.apex.keybindings.SavedKeyBinding.isShiftDown()) costToMax(pre, perk, level)?.let {
			event.lines.add(Component.literal("§7To max (${perk.maxLevel}): §b${ApexFormatters.withThousands(it)} $powder Powder"))
		} else event.lines.add(Component.literal("§8Hold Shift for the cost to max"))
	}
}
