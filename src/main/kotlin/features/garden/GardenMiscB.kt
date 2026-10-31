// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component
import org.lwjgl.sdl.SDLVideo

@OptIn(NeedsComponents::class)
object GardenMiscB {

	val seasonPerks = listOf(
		"§dSpring" to "§6+25☘ Farming Fortune", "§6Summer" to "§3+20 Farming Wisdom",
		"§eAutumn" to "§cPests §7spawn §a15% §7more often", "§9Winter" to "§7Visitors give §a5% §7more §cCopper",
	)

	fun seasonIndex(now: Long): Int {
		val sinceYearStart = Math.floorMod(now - SkyBlockEventTimers.EPOCH, SkyBlockEventTimers.YEAR)
		val month = sinceYearStart / SkyBlockEventTimers.MONTH
		return (month / 3).toInt()
	}

	fun atmosphericLine(now: Long = System.currentTimeMillis()): Component? {
		if (!TConfig.atmosphericFilter) return null
		val entry = seasonPerks[seasonIndex(now)]
		return Component.literal("${entry.first}§7: ${entry.second}")
	}

	val vinylPests = mapOf(
		"Not Just a Pest" to "Beetle", "Cricket Choir" to "Cricket", "Earthworm Ensemble" to "Earthworm", "Pretty Fly" to "Fly",
		"Cicada Symphony" to "Locust", "DynaMITES" to "Mite", "Buzzin' Beats" to "Mosquito", "Wings of Harmony" to "Moth",
		"Rodent Revolution" to "Rat", "Slow and Groovy" to "Slug", "Pray For Me" to "Praying Mantis",
		"Firefly in the Hole" to "Firefly", "Imagine Dragonflies" to "Dragonfly",
	)
	private val nowPlaying = Regex("\\s*Now Playing: (.+?)\\s*")
	var vinyl: String? = null

	fun parsePlaying(lore: List<String>): String? {
		for (line in lore) nowPlaying.matchEntire(line)?.let { return it.groupValues[1] }
		return null
	}

	@Subscribe
	fun onStereoMenu(event: ChestInventoryUpdateEvent) {
		if (Client.screenTitle != "Stereo Harmony") return
		val slots = Client.containerScreen?.menu?.slots ?: return
		for (slot in slots) {
			val playing = parsePlaying(slot.item.view().loreAccordingToNbt.map { it.bareText }) ?: continue
			vinyl = if (playing == "None") null else playing
		}
	}

	fun stereoLine(): Component? {
		if (!TConfig.stereoHarmony) return null
		val current = vinyl ?: return Component.literal("§ePlaying: §7Nothing")
		val pest = vinylPests[current]
		val suffix = if (pest == null) "" else " §7(§c$pest§7)"
		return Component.literal("§ePlaying: §a$current$suffix")
	}

	private val visited = Regex("\\s*Times Visited: ([\\d,]+)")
	private val accepted = Regex("\\s*Offers Accepted: ([\\d,]+)")

	private fun firstNumber(lore: List<String>, pattern: Regex): Double? {
		for (line in lore) {
			val m = pattern.matchEntire(line) ?: continue
			return m.groupValues[1].replace(",", "").toDouble()
		}
		return null
	}

	fun acceptRate(lore: List<String>): Double? {
		val visits = firstNumber(lore, visited) ?: return null
		val accepts = firstNumber(lore, accepted) ?: return null
		if (!(visits > 0)) return null
		return accepts / visits * 100
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		val screen = Client.screenTitle ?: return
		val lore = event.lines.map { it.bareText }
		if (TConfig.zorroReminder && screen.endsWith("Your Contests") && lore.any { "Click to claim" in it })
			event.lines.add(Component.literal("§c⚠ Equip Zorro's Cape before claiming for extra rewards!"))
		if (!TConfig.logbookStats || !screen.endsWith("Visitor's Logbook")) return
		val rate = acceptRate(lore) ?: return
		event.lines.add(Component.literal("§7Accept rate: §a%.1f%%".format(rate)))
	}

	private var seeThrough = false

	private fun applyOpacity() {
		val opacity = if (!seeThrough) 1f else (TConfig.seeThroughOpacity / 100f).coerceIn(0.05f, 1f)
		SDLVideo.SDL_SetWindowOpacity(Client.window.handle(), opacity)
	}

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (!TConfig.seeThroughWindow || !event.matches(TConfig.seeThroughKey)) return
		seeThrough = !seeThrough
		applyOpacity()
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		if (!seeThrough) return
		seeThrough = false
		applyOpacity()
	}
}
