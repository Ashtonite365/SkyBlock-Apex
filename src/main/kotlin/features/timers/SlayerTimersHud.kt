// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.timers

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.skills.SlayerOverlay
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.bareText

object SlayerTimersHud {
	val state = SlayerTimerState()

	private const val QUEST_HEADER = "Slayer Quest"

	private fun bestTime(boss: String): Duration? = SlayerOverlay.DConfig.data.bestBossMillis[boss]?.milliseconds

	private fun clock(d: Duration) = ApexFormatters.timespanText(d)

	@Subscribe
	fun onTick(event: TickEvent) {
		val sidebar = ScoreboardUtil.simplifiedScoreboardLines.map(String::trim)
		val header = sidebar.indexOf(QUEST_HEADER)
		val (boss, progress) = if (header < 0) null to null else sidebar.getOrNull(header + 1) to sidebar.getOrNull(header + 2)
		state.onScoreboard(boss, progress, ::bestTime)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) = state.onChat(event.bareText.trim())

	private fun bossRow(): Timers.TimerEntry? {
		val settings = Timers.TConfig
		val fighting = state.bossElapsed()
		if (fighting != null) {
			val best = state.pbAtSpawn
			val ahead = settings.slayerPbPace && best != null && fighting <= best
			return Timers.TimerEntry("slayer-boss", "Slayer Boss", (if (ahead) "§a" else "§c") + clock(fighting), true)
		}
		if (!settings.slayerLastKill) return null
		val kill = state.recentKill() ?: return null
		val versusBest = kill.pbDelta?.let { " §7(" + SlayerTimerState.formatDelta(it, ::clock) + "§7 PB)" }.orEmpty()
		return Timers.TimerEntry("slayer-boss", "Last Kill", "§e" + clock(kill.bossTime) + versusBest, false)
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		if (state.questName != null) state.questElapsed()?.let { event.add(Timers.TimerEntry("slayer-quest", "Slayer Quest", "§f" + clock(it), false)) }
		bossRow()?.let(event::add)
		if (!SlayerOverlay.TConfig.trackPbs) return
		state.questName?.let(::bestTime)?.let { event.add(Timers.TimerEntry("slayer-pb", "Slayer PB", "§a" + clock(it), false)) }
	}
}
