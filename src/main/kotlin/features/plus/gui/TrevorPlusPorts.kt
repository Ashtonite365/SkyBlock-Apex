// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.gui

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.misc.TrevorHelper
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText
import kotlin.time.Duration.Companion.minutes
import net.minecraft.network.chat.Component

object TrevorPlusPorts {

	private val REWARDS = listOf(
		Regex("^Your mob died randomly, you are rewarded (\\d+) pelts\\.$") to true,
		Regex("^Killing the animal rewarded you (\\d+) pelts\\.$") to false,
	)

	fun parsePelts(line: String): Pair<Int, Boolean>? {
		val text = line.trim()
		for ((pattern, selfKill) in REWARDS) {
			val match = pattern.matchEntire(text) ?: continue
			return match.groupValues[1].toInt() to selfKill
		}
		return null
	}

	val tracker = SessionTracker(idleTimeout = 5.minutes, resetAfter = 60.minutes)

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TrevorHelper.TConfig.tracker) return
		val line = event.bareText
		val quest = TrevorHelper.task(line)
		if (quest != null) {
			tracker.add("Quests")
			tracker.add("${quest.first} animals")
			return
		}
		val (pelts, selfKill) = parsePelts(line) ?: return
		tracker.add("Pelts", pelts.toDouble())
		tracker.add(if (selfKill) "Self-killing animals" else "Killed animals")
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TrevorHelper.TConfig.tracker || tracker.isEmpty) return
		val lines = ArrayList<Component>()
		lines += Component.literal("§b§lTrevor Tracker")
		lines += tracker.lines()
		event.drawHudLines(TrevorHelper.TConfig.trackerHud, lines)
	}
}
