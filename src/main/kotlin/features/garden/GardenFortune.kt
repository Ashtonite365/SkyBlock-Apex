// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object GardenFortune {
	private val patterns = PlusPatterns.group("garden.fortune")

	private val baseLine by patterns.pattern("tab.universal", "\\s*Farming Fortune: ☘?(?<fortune>\\d+)")

	private val cropLine by patterns.pattern("tab.crop", "\\s*(?<crop>[A-Za-z ]+?) Fortune: ☘?(?<fortune>\\d+)")

	private const val CONTEST_SECONDS = 1200.0

	fun tabFortune(tab: List<String>, crop: Crop?): Double? {
		var base: Double? = null
		for (line in tab) { base = baseLine.matchMatcher(line) { group("fortune").toDouble() }; if (base != null) break }
		if (base == null) return null
		var bonus = 0.0
		if (crop != null) for (line in tab) {
			val v = cropLine.matchMatcher(line) { if (group("crop").equals(crop.display, ignoreCase = true)) group("fortune").toDouble() else null }
			if (v != null) { bonus = v; break }
		}
		return base + bonus
	}

	fun contestCrops(ff: Double, crop: Crop, blocksPerSecond: Double = 20.0): Long =
		(CONTEST_SECONDS * blocksPerSecond * crop.baseDrops * (1 + ff / 100)).toLong()

	fun hudLines(crop: Crop?): List<Component> {
		val showFortune = TConfig.fortuneDisplay
		val showEstimate = TConfig.contestEstimate
		if (!showFortune && !showEstimate) return emptyList()
		val ff = tabFortune(TabListReader.currentSnapshot.body.map { it.bareText }, crop) ?: return emptyList()
		return buildList {
			if (showFortune) add(Component.literal("§6Farming Fortune: §f☘%.0f".format(ff)))
			if (showEstimate && crop != null) {
				val crops = ApexFormatters.compactNumber(contestCrops(ff, crop).toDouble())
				add(Component.literal("§eContest estimate: §f$crops §7${crop.display} (20 bps)"))
			}
		}
	}
}
