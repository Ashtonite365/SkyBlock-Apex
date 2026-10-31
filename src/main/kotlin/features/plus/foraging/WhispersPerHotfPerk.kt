// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.foraging

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.bareText
import kotlin.math.pow
import net.minecraft.network.chat.Component

object WhispersPerHotfPerk {
	@Config
	object TConfig : ManagedConfig("hotf-whispers", Category.FORAGING) {
		val whispersSpent by toggle("whispers-spent") { true }
		val whispersFor10Levels by toggle("whispers-for-10-levels") { true }
	}

	private fun powCurve(exponent: Double): (Int) -> Double = { n -> (n + 1.0).pow(exponent) }

	enum class Perk(val guiName: String, val maxLevel: Int, val cost: (Int) -> Double) {
		SWEEP("Sweep", 50, powCurve(3.0)),
		FORAGING_FORTUNE("Foraging Fortune", 50, powCurve(3.105)),
		STRENGTH_BOOST("Strength Boost", 50, powCurve(3.1)),
		SPEED_BOOST("Speed Boost", 50, powCurve(3.1)),
		LUCK_OF_THE_FOREST("Luck of the Forest", 40, powCurve(3.07)),
		DAILY_WISHES("Daily Wishes", 100, { n -> 200.0 + n * 18.0 }),
		GIFTS_250("250 Gifts", 40, powCurve(3.07)),
		DEEP_WATERS("Deep Waters", 50, powCurve(2.9)),
		EFFICIENT_FORAGER("Efficient Forager", 100, powCurve(2.6)),
		COLLECTOR("Collector", 50, powCurve(2.9)),
		FOREST_STRENGTH("Forest Strength", 50, powCurve(3.4)),
		HUNTERS_LUCK("Hunter's Luck", 50, powCurve(3.2)),
		GALATEAS_MIGHT("Galatea's Might", 50, powCurve(3.2)),
		ESSENCE_FORTUNE("Essence Fortune", 50, powCurve(3.2)),
		FOREST_SPEED("Forest Speed", 50, powCurve(3.4)),
		HALF_EMPTY("Half Empty", 25, powCurve(4.1)),
		RICOCHET("Ricochet", 10, powCurve(5.5)),
		HALF_FULL("Half Full", 25, powCurve(4.1)),
		;

		fun totalCost(level: Int): Long {
			var sum = 0L
			for (l in 2..level) sum += cost(l).toInt().toLong()
			return sum
		}

		val totalCostMax get() = totalCost(maxLevel)

		companion object {
			fun byName(name: String): Perk? {
				val wanted = name.trim()
				return entries.firstOrNull { it.guiName == wanted }
			}
		}
	}

	private val LEVEL_LINE = Regex("^Level (\\d+).*")

	fun level(lore: List<String>): Int? {
		for (line in lore) {
			val m = LEVEL_LINE.matchEntire(line.trim()) ?: continue
			return m.groupValues[1].toInt()
		}
		return null
	}

	private fun commas(n: Long) = ApexFormatters.withThousands(n)

	fun spentLine(perk: Perk, level: Int): String {
		val spent = perk.totalCost(level)
		val max = perk.totalCostMax
		if (level >= perk.maxLevel) return "§7Whispers spent: §e${commas(max)} §7(§aMax level§7)"
		val pct = "%.2f".format(spent * 100.0 / max)
		return "§7Whispers spent: §e${commas(spent)}§7/§e${commas(max)}§7 (§e$pct%§7)"
	}

	fun next10(perk: Perk, level: Int): Pair<Int, Long> {
		val target = minOf(level + 10, perk.maxLevel)
		return Pair(target - level, perk.totalCost(target) - perk.totalCost(level))
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!SkyBlockSession.inSkyBlock || Client.screenTitle != "Heart of the Forest") return
		val perk = Perk.byName(event.stack.hoverName.bareText) ?: return
		val level = level(event.lines.map { it.bareText }) ?: return
		val insertAt = minOf(2, event.lines.size)
		if (TConfig.whispersFor10Levels && SavedKeyBinding.isShiftDown() && level < perk.maxLevel) {
			val (gained, cost) = next10(perk, level)
			val plural = if (gained == 1) "" else "s"
			event.lines.add(insertAt, Component.literal("§7Whispers for $gained level$plural: §e${commas(cost)}"))
		}
		if (TConfig.whispersSpent) event.lines.add(insertAt, Component.literal(spentLine(perk, level)))
	}
}
