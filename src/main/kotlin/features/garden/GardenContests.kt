// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object GardenContests {
	enum class Bracket(val color: String) { DIAMOND("b"), PLATINUM("3"), GOLD("6"), SILVER("f"), BRONZE("c") }

	private val group = PlusPatterns.group("garden.contest")
	private val bracketLine by group.pattern("bracket", "\\s*(?<b>DIAMOND|PLATINUM|GOLD|SILVER|BRONZE) \\(Top \\d{1,2}%\\): (?<amount>[\\d,]+)")
	private val timeLeft by group.pattern("tab.timeleft", "\\s*Jacob's Contest: (?<t>\\d+[smh]+) left")
	private val tabCrop by group.pattern("tab.crop", "\\s*(?:○|☘) (?<crop>[A-Za-z ]+?)(?: [\\dmsh]+.*)?")

	fun parseBrackets(lore: List<String>): Map<Bracket, Long> {
		val out = LinkedHashMap<Bracket, Long>()
		for (line in lore) {
			val parsed = bracketLine.matchMatcher(line) { Bracket.valueOf(group("b")) to group("amount").replace(",", "").toLong() } ?: continue
			out[parsed.first] = parsed.second
		}
		return out
	}

	fun parseActive(tab: List<String>): Pair<String, List<String>>? {
		for ((i, line) in tab.withIndex()) {
			val left = timeLeft.matchMatcher(line) { group("t") } ?: continue
			val crops = ArrayList<String>()
			for (j in i + 1 until minOf(i + 4, tab.size)) tabCrop.matchMatcher(tab[j]) { group("crop") }?.let { crops.add(it) }
			return left to crops
		}
		return null
	}

	fun nextContestLine(now: Long = System.currentTimeMillis()): Component? {
		if (!TConfig.nextContestDisplay) return null
		val active = parseActive(TabListReader.currentSnapshot.body.map { it.bareText })
		if (active != null) {
			val (left, crops) = active
			return Component.literal("§eJacob's Contest: §f$left left §7(${crops.joinToString()})")
		}
		val start = SkyBlockEventTimers.nextHourly(15, 20, now).first
		val secs = maxOf(start - now, 0L) / 1000
		return Component.literal("§eNext Jacob's Contest: §f${secs / 60}m ${secs % 60}s")
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.contestBrackets) return
		val lore = event.lines.map { it.bareText }
		val brackets = parseBrackets(lore)
		if (brackets.isEmpty()) return
		val crop = Crop.entries.firstOrNull { c -> lore.any { "${c.display} Contest" in it } } ?: return
		event.lines.add(Component.literal("§7FF needed at 20 bps:"))
		for ((bracket, amount) in brackets) {
			val ff = maxOf(GardenProgress.ffNeeded(amount, crop), 0.0)
			event.lines.add(Component.literal(" §${bracket.color}${bracket.name}§7: §f%.0f ☘".format(ff)))
		}
	}

	private var contestStart: Long? = null
	private var blocks = 0

	fun summary(blocksBroken: Int, millis: Long): String {
		val secs = maxOf(millis / 1000, 1L)
		val template = "§eContest stats: §f" + ApexFormatters.withThousands(blocksBroken.toLong(), 0) +
			" §7blocks, §f%.2f §7bps over §b" + (secs / 60) + "m " + (secs % 60) + "s"
		return template.format(blocksBroken.toDouble() / secs)
	}

	@Subscribe
	fun onBreak(event: AttackBlockEvent) {
		if (contestStart == null || event.player !== Client.player) return
		blocks += 1
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		if (!TConfig.contestSummary || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) {
			contestStart = null
			return
		}
		val running = parseActive(TabListReader.currentSnapshot.body.map { it.bareText }) != null
		val start = contestStart
		if (running && start == null) {
			contestStart = System.currentTimeMillis()
			blocks = 0
		} else if (!running && start != null) {
			if (blocks > 0) Client.showChat(Component.literal(summary(blocks, System.currentTimeMillis() - start)))
			contestStart = null
		}
	}
}
