// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.mining

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.mining.MiningExtras.TConfig
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText

object MiningPlusPorts {

	private val MAGIC_FIND_DROP = Regex("^RARE DROP! (.+) \\(\\+(\\d+)%? ✯ Magic Find\\)$")
	private val COMBO_EXPIRED = Regex("^Your Kill Combo has expired! You reached a ([\\d,.]+) Kill Combo!$")
	private const val BAG_OF_CASH = "The ghost's death materialized 1,000,000 coins from the mists!"

	private val LOOT_ENTRY = Regex("^\\s{2,}(?:\\+?([\\d,]+)x? )?(.+?)(?: x([\\d,]+))?$")
	private val CORPSE_HEADER = Regex("^(\\w+) CORPSE LOOT!$")
	private const val RESOURCEFUL = "LUCKY! Your Resourceful perk saved your key from being consumed!"
	private const val NUCLEUS_HEADER = "CRYSTAL NUCLEUS LOOT BUNDLE"
	private const val EXCAVATION_HEADER = "EXCAVATION COMPLETE"
	private const val RULE = '▬'
	private val ALERTS: List<Pair<Regex, String>> = listOf(
		Regex("^EXCAVATOR! You found a Suspicious Scrap!$") to "§9Suspicious Scrap",
		Regex("^A Golden Goblin has spawned!$") to "§6Golden Goblin",
		Regex("^A Diamond Goblin has spawned!$") to "§bDiamond Goblin",
	)

	data class GhostDrop(val item: String, val magicFind: Int)

	fun parseGhostMf(line: String): GhostDrop? {
		val m = MAGIC_FIND_DROP.matchEntire(line.trim()) ?: return null
		val (item, mf) = m.destructured
		return GhostDrop(item, mf.toInt())
	}

	fun parseKillCombo(line: String): Int? {
		val reached = COMBO_EXPIRED.matchEntire(line.trim())?.groupValues?.get(1) ?: return null
		return reached.filter { it != ',' && it != '.' }.toIntOrNull()
	}

	private fun isRule(text: String) = text.all { it == RULE }

	fun parseLootLine(line: String): Pair<String, Int>? {
		val m = LOOT_ENTRY.matchEntire(line) ?: return null
		val (lead, rawName, tail) = m.destructured
		val name = rawName.trim()
		if (name.isEmpty() || isRule(name)) return null
		val digits = (if (lead.isNotEmpty()) lead else tail).replace(",", "")
		return name to (digits.toIntOrNull() ?: 1)
	}

	fun corpseType(line: String): String? = CORPSE_HEADER.matchEntire(line.trim())?.groupValues?.get(1)

	fun notification(line: String): String? {
		val text = line.trim()
		for ((pattern, title) in ALERTS) if (pattern.matches(text)) return title
		return null
	}

	var maxKillCombo = 0
	var mfTotal = 0L
	var mfDrops = 0

	val nucleus = SessionTracker(idleTimeout = 10.minutes, resetAfter = 60.minutes)
	val excavator = SessionTracker(idleTimeout = 5.minutes, resetAfter = 60.minutes)
	val corpses = SessionTracker(idleTimeout = 15.minutes, resetAfter = 60.minutes)

	private var openBundle: SessionTracker? = null

	private fun open(into: SessionTracker, counter: String) {
		openBundle = into
		into.add(counter)
	}

	private fun ghostLine(line: String) {
		parseGhostMf(line)?.let { drop -> mfTotal += drop.magicFind; mfDrops++ }
		parseKillCombo(line)?.let { combo -> maxKillCombo = maxOf(maxKillCombo, combo) }
		if (line.trim() == BAG_OF_CASH) MiningExtrasBridge.ghosts.add("Bag of Cash (1M)")
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText
		val island = SkyBlockSession.skyblockIsland
		if (TConfig.ghostTracker && TConfig.ghostTrackerExtended && island == SkyBlockIsland.DWARVEN_MINES) ghostLine(line)
		val text = line.trim()
		if (TConfig.miningNotifications) {
			val title = notification(line)
			if (title != null) dev.ashtonite365.apex.util.Alerts.show(Component.literal(title), 2.seconds)
		}
		if (TConfig.corpseLoot) {
			val type = corpseType(line)
			if (type != null) {
				open(corpses, "${type.lowercase().replaceFirstChar(Char::uppercase)} corpses")
				return
			}
			if (text == RESOURCEFUL) corpses.add("Keys saved (Resourceful)")
		}
		when {
			text.startsWith(NUCLEUS_HEADER) && TConfig.nucleusTracker -> { open(nucleus, "Runs"); return }
			text.startsWith(EXCAVATION_HEADER) && TConfig.excavatorTracker -> { open(excavator, "Excavations"); return }
		}
		val target = openBundle ?: return
		if (text.isNotEmpty() && isRule(text)) { openBundle = null; return }
		if (text.isEmpty() || text == "REWARDS") return
		val (name, amount) = parseLootLine(line) ?: return
		target.add(name, amount.toDouble())
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val island = SkyBlockSession.skyblockIsland
		if (TConfig.nucleusTracker && !nucleus.isEmpty && island == SkyBlockIsland.CRYSTAL_HOLLOWS)
			event.drawHudLines(TConfig.nucleusHud, listOf(Component.literal("§5Crystal Nucleus")) + nucleus.lines())
		if (TConfig.excavatorTracker && !excavator.isEmpty && island == SkyBlockIsland.DWARVEN_MINES)
			event.drawHudLines(TConfig.excavatorHud, listOf(Component.literal("§6Fossil Excavator")) + excavator.lines())
		if (TConfig.corpseLoot && !corpses.isEmpty && island == SkyBlockIsland.MINESHAFT)
			event.drawHudLines(TConfig.corpseLootHud, listOf(Component.literal("§bCorpse loot")) + corpses.lines())
		if (!(TConfig.ghostTracker && TConfig.ghostTrackerExtended && island == SkyBlockIsland.DWARVEN_MINES)) return
		val ghosts = MiningExtrasBridge.ghosts
		if (mfDrops <= 0 && maxKillCombo <= 0 && ghosts.isEmpty) return
		val average = if (mfDrops == 0) "-" else "%.1f".format(mfTotal.toDouble() / mfDrops)
		val lines = ArrayList<Component>()
		lines += Component.literal("§5Ghost drops")
		lines += ghosts.lines()
		lines += Component.literal("§7Avg Magic Find: §b$average")
		lines += Component.literal("§7Max Kill Combo: §e$maxKillCombo")
		event.drawHudLines(TConfig.ghostHud, lines)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MINING, "resetnucleus", "Reset the Crystal Nucleus and Fossil Excavator trackers") {
			onRun {
				nucleus.reset(); excavator.reset(); corpses.reset()
				maxKillCombo = 0; mfTotal = 0; mfDrops = 0
			}
		}
	}
}

internal object MiningExtrasBridge {
	val ghosts get() = dev.ashtonite365.apex.features.mining.MiningExtras.ghosts
}
