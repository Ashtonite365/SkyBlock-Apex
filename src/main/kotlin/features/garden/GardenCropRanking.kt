// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object GardenCropRanking {
	private const val SHOWN = 5
	private const val IDLE_BPS = 20.0

	fun cropsPerHour(crop: Crop, bps: Double, ff: Double) = bps * 3600 * crop.baseDrops * (1 + ff / 100)

	fun moneyRanking(bps: Double, ff: Double, price: (Crop) -> Double?): List<Pair<Crop, Double>> {
		val rows = ArrayList<Pair<Crop, Double>>()
		for (crop in Crop.entries) { val each = price(crop) ?: continue; rows += crop to cropsPerHour(crop, bps, ff) * each }
		return rows.sortedWith(compareByDescending { it.second })
	}

	fun timeRanking(bps: Double, ff: Double, left: (Crop) -> Long?): List<Pair<Crop, Double>> {
		val rows = ArrayList<Pair<Crop, Double>>()
		for (crop in Crop.entries) { val remaining = left(crop) ?: continue; rows += crop to remaining / cropsPerHour(crop, bps, ff) }
		return rows.sortedWith(compareBy { it.second })
	}

	fun formatHours(h: Double): String {
		val minutes = (h * 60).toLong()
		if (minutes < 60) return "${minutes}m"
		return "${minutes / 60}h ${minutes % 60}m"
	}

	private fun rawCropPrice(crop: Crop) = GardenHelpers.cropForTool(crop.toolPrefixes.first())?.let(RepoConstants::price)

	private fun hoursLeft(crop: Crop, totals: Map<String, Long>, garden: com.google.gson.JsonObject?): Long? {
		val total = totals[crop.name] ?: return null
		val milestone = GardenProgress.milestoneFromTotal(GardenProgress.milestoneTable(garden, crop), total)
		return milestone.needed?.let { it - milestone.progress }
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val money = TConfig.cropMoneyRanking
		val time = TConfig.bestCropTime
		if (!money && !time || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val bps = GardenCropSpeed.currentBps().takeIf { it > 0 } ?: IDLE_BPS
		val ff = GardenFortune.tabFortune(TabListReader.currentSnapshot.body.map { it.bareText }, null) ?: 0.0
		val lines = ArrayList<Component>()
		if (money) {
			lines += Component.literal("§6Crop coins/h §7(%.1f bps, ☘%.0f)".format(bps, ff))
			for ((crop, coins) in moneyRanking(bps, ff, ::rawCropPrice).take(SHOWN))
				lines += Component.literal(" §e${crop.display}§7: §6${ApexFormatters.compactNumber(coins)}")
		}
		if (time) {
			val garden = RepoConstants.get("garden")
			val totals = GardenProgress.DConfig.data?.totals.orEmpty()
			lines += Component.literal("§aNext milestone")
			for ((crop, hours) in timeRanking(bps, ff) { hoursLeft(it, totals, garden) }.take(SHOWN))
				lines += Component.literal(" §e${crop.display}§7: §f${formatHours(hours)}")
		}
		event.drawPlusLines(TConfig.cropRankingPosition, lines)
	}
}
