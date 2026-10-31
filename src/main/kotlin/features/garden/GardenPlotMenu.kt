// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.features.plus.garden.GardenPlots
import dev.ashtonite365.apex.features.plus.garden.SprayTracker
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText

@OptIn(NeedsComponents::class)
object GardenPlotMenu {
	const val MENU = "Configure Plots"

	private const val ROW = 9
	private const val FIRST_COLUMN = 2
	private val GRID = 0..4

	@Serializable
	data class Icons(val icons: MutableMap<String, String> = mutableMapOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Icons>(serializer(), "garden-plot-icons", ::Icons)

	fun slotOf(plotId: Int): Int? {
		val row = GardenPlots.plotMap.indexOfFirst { plotId in it }
		if (row < 0) return null
		return row * ROW + FIRST_COLUMN + GardenPlots.plotMap[row].indexOf(plotId)
	}

	fun plotAtSlot(slot: Int): Int? {
		val row = slot / ROW
		val column = slot % ROW - FIRST_COLUMN
		return if (row in GRID && column in GRID) GardenPlots.plotMap[row][column] else null
	}

	enum class Status(val colour: Int) { PESTS(0x80FF5555.toInt()), SPRAYS(0x80FFFF55.toInt()), LOCKED(0x80555555.toInt()), CURRENT(0x8055FF55.toInt()), PASTING(0x80AA00AA.toInt()) }

	private val PEST_COUNT = Regex(".*This plot has (\\d+) .*Pests?!.*")
	private val PLOT_TITLE = Regex("Plot - (.+)")

	data class PlotInfo(val name: String?, val pests: Int, val locked: Boolean, val pasting: Boolean)

	fun readPlot(hoverName: String, lore: List<String>): PlotInfo {
		val name = PLOT_TITLE.matchEntire(hoverName.trim())?.groupValues?.get(1)
		var pests: Int? = null
		var locked = false
		var pasting = false
		for (line in lore) {
			if (pests == null) pests = PEST_COUNT.matchEntire(line)?.groupValues?.get(1)?.toIntOrNull()
			if ("Cost:" in line) locked = true
			if ("Pasting in progress:" in line) pasting = true
		}
		return PlotInfo(name, pests ?: 0, locked, pasting)
	}

	fun status(info: PlotInfo, plotId: Int, currentPlot: Int?, sprayMinutes: Long?): Pair<Status, Int?>? {
		if (info.pests > 0) return Status.PESTS to info.pests
		if (sprayMinutes != null) return Status.SPRAYS to sprayMinutes.toInt()
		val flag = when {
			info.locked -> Status.LOCKED
			currentPlot == plotId -> Status.CURRENT
			info.pasting -> Status.PASTING
			else -> return null
		}
		return flag to null
	}

	@OptIn(ExpensiveItemCacheApi::class)
	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (Client.screenTitle != MENU) return
		val slot = event.slot
		if (slot.container is Inventory || slot.item.isEmpty) return
		val plot = plotAtSlot(slot.containerSlot) ?: return
		val ctx = event.context
		val icon = if (TConfig.plotIcons) DConfig.data?.icons?.get("$plot") else null
		if (icon != null) {
			ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xFF8B8B8B.toInt())
			ctx.item(SBItemStack(ItemId(icon)).asImmutableItemStack().current(), slot.x, slot.y)
		}
		if (!TConfig.plotMenuHighlight) return
		val info = readPlot(slot.item.hoverName.bareText, slot.item.view().loreAccordingToNbt.map { it.bareText })
		val now = System.currentTimeMillis()
		val spray = info.name?.let { SprayTracker.sprays[it] }?.expiresAt?.takeIf { it > now }?.let { (it - now) / 60_000 + 1 }
		val (state, number) = status(info, plot, GardenPlots.currentPlotId(), spray) ?: return
		ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, state.colour)
		number?.toString()?.let { ctx.text(Client.font, it, slot.x + 17 - Client.font.width(it), slot.y + 9, -1, true) }
	}

	private val COST_TITLE = Regex("\\s*Cost:?\\s*")
	private val COST_ENTRY = Regex("^\\s*([A-Za-z' \\-]+?)(?: x([\\d,]+))?\\s*$")

	fun costLine(lore: List<String>): Pair<Int, Pair<String, Long>>? {
		val heading = lore.indexOfFirst { it.trim().startsWith("Cost") || COST_TITLE.matches(it) }
		val at = heading + 1
		if (heading < 0 || at >= lore.size) return null
		val entry = COST_ENTRY.matchEntire(lore[at]) ?: return null
		val (item, amount) = entry.destructured
		return at to (item.trim() to (amount.replace(",", "").toLongOrNull() ?: 1L))
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.nextPlotPrice || Client.screenTitle != MENU) return
		if (!event.stack.hoverName.bareText.trim().startsWith("Plot")) return
		val (at, cost) = costLine(event.lines.map { it.bareText }) ?: return
		val price = ItemNameLookup.guessItemByName(cost.first, false)?.let(RepoConstants::price) ?: return
		event.lines[at] = event.lines[at].copy().append(Component.literal(" §7(§6${ApexFormatters.compactNumber(price * cost.second)}§7)"))
	}

	private fun setIcon(plotArg: String, itemArg: String): String {
		val plot = plotArg.toIntOrNull()?.takeIf { slotOf(it) != null }
		val icons = DConfig.data?.icons
		if (plot == null || icons == null) return "§cUnknown plot (0-24)."
		val item = itemArg.uppercase()
		val reset = item == "RESET"
		if (reset) icons.remove("$plot") else icons["$plot"] = item
		DConfig.markDirty()
		return "§ePlot $plot icon: §f${if (reset) "default" else item}"
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.GARDEN, "ploticon", "Set the Configure Plots icon of a plot: <plot> <SKYBLOCK_ID|reset>") {
			onArgument("plot", StringArgumentType.word()) { plotArg ->
				onArgument("item", StringArgumentType.word()) { itemArg ->
					onRun { source.sendFeedback(Component.literal(setIcon(this[plotArg], this[itemArg]))) }
				}
			}
		}
	}
}
