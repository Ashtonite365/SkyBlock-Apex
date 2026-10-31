// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import com.mojang.brigadier.arguments.IntegerArgumentType
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

@OptIn(NeedsComponents::class)
object GardenMiscA {
	private val upgradeTier = Regex("\\s*Current Tier: (\\d+)/\\d+.*")
	private val compostAvailable = Regex("\\s*Compost Available: ([\\d,]+).*")
	private val composterValue = Regex(".* ([\\d,.]+)/([\\d,.]+[kKmM]?)\\s*")

	private fun firstMatch(lore: List<String>, pattern: Regex): MatchResult? {
		for (line in lore) pattern.matchEntire(line)?.let { return it }
		return null
	}

	fun slotNumber(screen: String, name: String, lore: List<String>): String? {
		when (screen) {
			"Crop Milestones" -> {
				GardenProgress.Crop.byDisplay(name.substringBeforeLast(' ')) ?: return null
				return GardenProgress.roman(name.substringAfterLast(' '))?.toString()
			}
			"Crop Upgrades" -> return firstMatch(lore, upgradeTier)?.groupValues?.get(1)
			"Composter Upgrades" -> {
				val tier = GardenProgress.roman(name.substringAfterLast(' ')) ?: return null
				return if (' ' in name) tier.toString() else null
			}
			else -> return null
		}
	}

	fun composterNumber(slotIndex: Int, lore: List<String>): String? {
		if (slotIndex == 13) return firstMatch(lore, compostAvailable)?.let { "§6" + it.groupValues[1] }
		if (slotIndex != 46 && slotIndex != 52) return null
		val m = firstMatch(lore, composterValue) ?: return null
		val having = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		val colour = if (slotIndex == 46) "§e" else "§a"
		return colour + ApexFormatters.compactNumber(having) + "/" + m.groupValues[2]
	}

	private fun drawRight(event: SlotRenderEvents.After, text: String) {
		val slot = event.slot
		event.context.text(Client.font, text, slot.x + 17 - Client.font.width(text), slot.y + 9, -1, true)
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		val screen = Client.screenTitle ?: return
		val slot = event.slot
		if (slot.container is Inventory || slot.item.isEmpty) return
		val lore = { slot.item.view().loreAccordingToNbt.map { it.bareText } }
		if (screen == "Composter" && TConfig.composterInventoryNumbers) {
			composterNumber(slot.containerSlot, lore())?.let { drawRight(event, it) }
			return
		}
		if (!TConfig.inventoryNumbers) return
		val name = slot.item.hoverName.bareText.trim()
		slotNumber(screen, name, lore())?.let { drawRight(event, it) }
	}

	private const val AFK_MS = 30_000L
	private var lastBreak = 0L
	var activeMillis = 0L
		private set

	fun addGap(gap: Long): Long = if (gap >= 1 && gap < AFK_MS) gap else 0L

	@Subscribe
	fun onBreak(event: AttackBlockEvent) {
		if (!TConfig.gardenUptime || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN || event.player !== Client.player) return
		val now = System.currentTimeMillis()
		if (lastBreak != 0L) activeMillis += addGap(now - lastBreak)
		lastBreak = now
	}

	fun uptimeLine(): Component? {
		if (!TConfig.gardenUptime || activeMillis == 0L) return null
		val secs = activeMillis / 1000
		val h = secs / 3600
		val m = secs % 3600 / 60
		return Component.literal("§eFarming uptime: §f${h}h ${m}m ${secs % 60}s")
	}

	fun cropsIn(minutes: Int, perMinute: Double): Long = (minutes * perMinute).toLong()

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.GARDEN, "cropsin", "Crops you farm in <minutes> at your current rate (client-side)") {
			onArgument("minutes", IntegerArgumentType.integer(1, 10_000)) { minutesArg ->
				onRun {
					val rate = GardenTools.cropsPerMinute()
					val tool = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId
					val crop = tool?.let { GardenProgress.Crop.forTool(it) }
					val minutes = this[minutesArg]
					val text = if (rate == null || crop == null) "§cFarm with a counter tool for a bit first."
					else "§e${crop.display} in ${minutes}m: §f${ApexFormatters.withThousands(cropsIn(minutes, rate), 0)}"
					source.sendFeedback(Component.literal(text))
				}
			}
		}
	}
}
