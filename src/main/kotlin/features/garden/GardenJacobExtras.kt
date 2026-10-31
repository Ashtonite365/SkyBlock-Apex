// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.PlusRepoData
import dev.ashtonite365.apex.util.bareText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(NeedsComponents::class)
object GardenJacobExtras {
	private val contest = PlusPatterns.group("garden.contest")
	private val collectedNow by contest.pattern("personal.best.collection.new.colorless", "\\[NPC] Jacob: You collected (?<collected>[\\d,]+) items! PERSONAL BEST!")
	private val collectedBefore by contest.pattern("personal.best.collection.old.colorless", "\\[NPC] Jacob: Your previous Personal Best was (?<collected>[\\d,]+)\\.")
	private val perkFortune by contest.pattern("personal.best.ff.new.colorless",
		"\\[NPC] Jacob: Your Personal Bests perk is now granting you \\+(?<ff>[\\d.,]+)\\D*? (?<crop>.+) Fortune!")
	private val contestsMenu by contest.pattern("yourcontests", "(?:\\(\\d+/\\d+\\) )?Your Contests")
	private val dayTitle by contest.pattern("time.colorless", "(?<month>.+) (?<day>\\d+)(?:rd|st|nd|th), Year (?<year>\\d+)")
	private val bracketLine by contest.pattern("inventory.medal.colorless", "You placed in the (?<medal>[A-Z]+) bracket!")

	fun increments(): Map<String, Int> = try {
		val root = PlusRepoData.loadRepoJson("constants/Garden.json") as JsonObject?
		val table = root?.get("personal_best_increment") as JsonObject?
		table?.mapValues { (_, v) -> (v as JsonPrimitive).content.toInt() } ?: emptyMap()
	} catch (e: Throwable) {
		emptyMap()
	}

	fun cropKey(name: String): String = name.trim().uppercase().replace(' ', '_')

	fun gainMessage(newCollected: Double, oldCollected: Double, newFf: Double, crop: String, increment: Int, overflow: Boolean): String? {
		val perFortune = increment * 100.0
		val before = oldCollected / perFortune
		val uncapped = newCollected / perFortune
		if (overflow) {
			if (!(uncapped > 100)) return null
			return "§eYou have §6%.2f☘ $crop Fortune §eincluding overflow, §6%.2f☘ §emore than previously!".format(Locale.ROOT, uncapped, uncapped - before)
		}
		if (!(before < 100)) return null
		return "§eThis is §6%.2f☘ $crop Fortune §emore than previously!".format(Locale.ROOT, newFf - before)
	}

	private object Seen {
		var collected: Double? = null
		var previous: Double? = null
		var perk: Pair<Double, String>? = null
		fun clear() { collected = null; previous = null; perk = null }
	}

	private fun number(text: String) = text.replace(",", "").toDouble()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.pbGain) return
		val text = event.bareText
		collectedNow.matcher(text).let { if (it.matches()) Seen.collected = number(it.group("collected")) }
		collectedBefore.matcher(text).let { if (it.matches()) Seen.previous = number(it.group("collected")) }
		perkFortune.matcher(text).let { if (it.matches()) Seen.perk = number(it.group("ff")) to it.group("crop") }
		val collected = Seen.collected ?: return
		val previous = Seen.previous ?: return
		val (fortune, crop) = Seen.perk ?: return
		Seen.clear()
		val increment = increments()[cropKey(crop)] ?: return
		val message = gainMessage(collected, previous, fortune, crop, increment, TConfig.pbGainOverflow) ?: return
		Client.showChat(Component.literal("§e[Apex] $message"))
	}

	private const val YEAR_ZERO = 1559829300000L
	private const val YEAR_MS = 124L * 3_600_000L
	private const val MONTH_MS = YEAR_MS / 12
	private const val DAY_MS = MONTH_MS / 31
	private val SEASONS = arrayOf("Spring", "Summer", "Autumn", "Winter")

	private fun monthIndex(name: String): Int {
		for ((i, season) in SEASONS.withIndex()) {
			when (name) {
				"Early $season" -> return i * 3
				season -> return i * 3 + 1
				"Late $season" -> return i * 3 + 2
			}
		}
		return -1
	}

	fun contestStart(itemName: String): Long? {
		val m = dayTitle.matcher(itemName.trim())
		if (!m.matches()) return null
		val month = monthIndex(m.group("month"))
		if (month < 0) return null
		val day = m.group("day").toLong() - 1
		return YEAR_ZERO + m.group("year").toLong() * YEAR_MS + month * MONTH_MS + day * DAY_MS
	}

	fun realTimeLine(start: Long): String {
		val clock = SimpleDateFormat("HH:mm", Locale.US)
		val date = SimpleDateFormat("dd MMMM yyyy", Locale.US).format(Date(start))
		val end = start + 20 * 60_000
		return "§7$date ${clock.format(Date(start))}-${clock.format(Date(end))}"
	}

	fun medal(lore: List<String>): String? {
		for (line in lore) {
			val m = bracketLine.matcher(line.trim())
			if (m.matches()) m.group("medal")?.let { return it }
		}
		return null
	}

	private fun medalColour(medal: String) = when (medal) {
		"BRONZE" -> "§c"; "SILVER" -> "§f"; "GOLD" -> "§6"; "PLATINUM" -> "§3"; "DIAMOND" -> "§b"
		else -> "§7"
	}

	private fun contestsOpen(): Boolean {
		val title = Client.screenTitle ?: return false
		return contestsMenu.matcher(title).matches()
	}

	@Subscribe
	fun onSlot(event: SlotRenderEvents.After) {
		if (!contestsOpen()) return
		val slot = event.slot
		if (slot.container is Inventory || slot.item.isEmpty) return
		val lore = slot.item.view().loreAccordingToNbt.map { it.bareText }
		if (TConfig.contestHighlightRewards && lore.lastOrNull()?.trim() == "Click to claim reward!")
			event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8055FF55.toInt())
		if (!TConfig.contestMedalIcon) return
		val placed = medal(lore) ?: return
		val boosted = lore.any { "Contest boosted by Finnegan!" in it }
		event.context.text(Client.font, medalColour(placed) + (if (boosted) "▲" else "✦"), slot.x + 9, slot.y, -1, true)
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.contestRealTime || !contestsOpen() || event.lines.size < 2) return
		if (event.lines.none { it.bareText.startsWith("Your score: ") }) return
		val start = contestStart(event.stack.hoverName.bareText) ?: return
		event.lines.add(1, Component.literal(realTimeLine(start)))
	}
}
