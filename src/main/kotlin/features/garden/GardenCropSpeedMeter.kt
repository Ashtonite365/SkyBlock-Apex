// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.repoItemId
import java.util.Locale
import net.minecraft.network.chat.Component

object GardenCropSpeedMeter {
	var enabled = false
	private var baseline: Map<String, Long> = emptyMap()
	private var crop: Crop? = null
	private var broken = 0
	private var results: List<String> = emptyList()
	private var previousRead: Map<String, Long> = emptyMap()

	data class Result(val crops: Long, val blocks: Int, val cropsPerBlock: Double, val fortune: Double)

	fun compute(crops: Long, blocks: Int, crop: Crop): Result? {
		if (blocks <= 0) return null
		val perBlock = crops.toDouble() / blocks
		return Result(crops, blocks, perBlock, perBlock * 100 / crop.baseDrops)
	}

	private fun clearAll() {
		baseline = emptyMap(); previousRead = emptyMap(); crop = null; broken = 0; results = emptyList()
	}

	private fun fixed3(v: Double) = "%.3f".format(Locale.ROOT, v)

	fun onMilestonesRead(totals: Map<String, Long>) {
		if (!enabled || totals == previousRead) return
		previousRead = totals.toMap()
		if (baseline.isEmpty()) {

			baseline = totals.toMap()
			crop = null
			results = emptyList()
			return
		}
		val current = crop ?: return
		val gained = (totals[current.name] ?: 0) - (baseline[current.name] ?: 0)
		val r = compute(gained, broken, current) ?: return
		results = listOf(
			"",
			"§6Calculation results",
			" §7Crops collected: ${ApexFormatters.withThousands(r.crops, 0)}",
			" §7Blocks broken: ${ApexFormatters.withThousands(r.blocks, 0)}",
			" §7Crops per Block: " + fixed3(r.cropsPerBlock),
			" §7Calculated farming Fortune: §e" + fixed3(r.fortune),
			"§cOpen /cropmilestones again to recalculate!",
		)
	}

	@Subscribe
	fun onBreak(event: AttackBlockEvent) {
		if (!enabled || baseline.isEmpty()) return
		if (event.player !== Client.player || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val tool = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId ?: return
		val broke = Crop.forTool(tool) ?: return
		if (broke != crop) {

			crop = broke
			broken = 0
			results = emptyList()
		}
		broken += 1
	}

	fun lines(): List<String> {
		val out = arrayListOf("§7Crop Speed Meter")
		val current = crop
		if (baseline.isEmpty()) out += "§cOpen §e/cropmilestones §cto start!"
		else if (current == null) out += "§cStart breaking blocks!"
		else {
			out += " §7Current ${current.display} counter: §e${ApexFormatters.withThousands(broken, 0)}"
			if (results.isEmpty()) out += "§cOpen §e/cropmilestones §cagain to calculate!" else out.addAll(results)
		}
		return out
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!enabled || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		event.drawHudLines(TConfig.cropSpeedMeterPosition, lines().map { Component.literal(it) })
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.GARDEN, "cropspeedmeter", "Toggle the Crop Speed Meter (crops per block and implied farming fortune)") {
			onRun {
				enabled = !enabled
				clearAll()
				val state = if (enabled) "§aEnabled" else "§cDisabled"
				source.sendFeedback(Component.literal("§eCrop Speed Meter $state"))
			}
		}
	}
}
