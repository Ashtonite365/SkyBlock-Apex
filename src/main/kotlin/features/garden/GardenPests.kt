// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.util.bazaarStockOf
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.features.plus.garden.GardenPlots
import dev.ashtonite365.apex.features.plus.garden.GardenPlots.drawPlotBorder
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.DataHolder
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ItemId
import kotlinx.serialization.serializer

object GardenPests {

	private val PEST_SPAWNED = Regex(".*\\b(?:\\d+ )?Pests? (?:has|have) spawned in Plot - (\\S+)!.*")
	private val TAB_PLOTS = Regex("\\s*Plots: (.+)")
	private val TAB_ALIVE = Regex("\\s*Alive: (\\d+)\\b.*")
	private val RARE_DROP = Regex("(?:RARE|PET) DROP! (.+?)(?: x([\\d,]+))? \\(.*\\)\\s*")

	private val KILL_REWARD = Regex("You received ([\\d,]+)x (.+?) for killing an? (.+?)!")

	private fun digits(text: String) = text.replace(",", "")

	var lastSpawnMillis: Long? = null

	var lastSpawnPlot: Int? = null

	fun parseSpawnPlot(msg: String): Int? {
		val m = PEST_SPAWNED.matchEntire(msg) ?: return null
		return m.groupValues[1].toIntOrNull()
	}

	fun parseKill(msg: String): Triple<String, Long, String>? {
		val m = KILL_REWARD.matchEntire(msg.trim()) ?: return null
		val (count, item, pest) = m.destructured
		val amount = digits(count).toLongOrNull() ?: return null
		return Triple(item.trim(), amount, pest.trim())
	}

	fun parseDrop(msg: String): Pair<String, Long>? {
		val m = RARE_DROP.matchEntire(msg) ?: return null
		val (item, count) = m.destructured
		return item.trim() to (digits(count).toLongOrNull() ?: 1L)
	}

	fun parseInfested(tabLines: List<String>): List<Int> {
		val m = tabLines.firstNotNullOfOrNull { TAB_PLOTS.matchEntire(it) } ?: return emptyList()
		val plots = ArrayList<Int>()
		for (part in m.groupValues[1].split(',')) part.trim().toIntOrNull()?.let(plots::add)
		return plots
	}

	fun parseAlive(tabLines: List<String>): Int? {
		for (line in tabLines) {
			val n = TAB_ALIVE.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
			if (n != null) return n
		}
		return null
	}

	fun formatAgo(ms: Long): String {
		val secs = ms / 1000
		if (secs < 60) return "${secs}s"
		return "${secs / 60}m ${secs % 60}s"
	}

	@Config
	object PestProfitData : DataHolder<ProfitTracker.Data>(serializer(), "garden-pest-profit", { ProfitTracker.Data() })

	fun sellPrice(bazaarSell: Double?, npcSell: Double?): Double? =
		if (bazaarSell != null && bazaarSell > 0) bazaarSell else npcSell

	fun sellPrice(id: String): Double? {
		val sbId = ItemId(id)
		val bazaar = HypixelStaticData.bazaarData[bazaarStockOf(sbId)]?.quickStatus?.sellPrice
		return sellPrice(bazaar, HypixelStaticData.npcSellPrice(ItemId(id)))
	}

	val tracker = ProfitTracker("Pest Profit", { PestProfitData.data }, ::sellPrice, onChange = { PestProfitData.markDirty() })

	fun resetAll() {
		tracker.resetSession()
		tracker.resetTotal()
	}

	fun hasCounted(d: ProfitTracker.Data) = d.items.isNotEmpty() || d.events > 0 || d.coins != 0.0

	private fun tabTexts() = TabListReader.currentSnapshot.body.map { it.bareText }

	fun alivePests(): Int? = parseAlive(tabTexts())

	fun infestedPlots(): List<Int> = parseInfested(tabTexts())

	private fun itemId(name: String) = ItemNameLookup.guessItemByName(name, false)?.skyblockId

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val msg = event.bareText
		if (PEST_SPAWNED.matches(msg)) {
			val now = System.currentTimeMillis()
			lastSpawnMillis = now
			lastSpawnPlot = parseSpawnPlot(msg)
			GardenPestTimers.onPestSpawn(now)
		}
		if (!TConfig.pestProfitTracker) return
		parseDrop(msg)?.let { (name, amount) -> itemId(name)?.let { tracker.addItem(it, amount) } }
		parseKill(msg)?.let { (name, amount, _) ->
			itemId(name)?.let { tracker.addItem(it, amount) }
			tracker.addEvent()
		}
	}

	fun spawnTimerLines(): List<Component> = GardenPestTimers.hudLines()

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.pestFinder || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		val plots = infestedPlots()
		if (plots.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for (plot in plots) {
				drawPlotBorder(plot, 0xFFFF5555.toInt())
				val middle = GardenPlots.plotMiddle(plot) ?: continue
				text(middle, Component.literal("§cPests: Plot $plot"))
			}
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.pestProfitTracker || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		if (hasCounted(tracker.current)) event.drawPlusLines(TConfig.pestProfitPosition, tracker.lines())
	}
}
