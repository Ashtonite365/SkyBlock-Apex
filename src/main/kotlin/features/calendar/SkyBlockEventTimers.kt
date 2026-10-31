// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.calendar

import kotlin.time.Duration.Companion.milliseconds
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.features.timers.Timers

object SkyBlockEventTimers {
	val identifier: String
		get() = "event-timers"

	const val EPOCH = 1560275700000L

	const val DAY = 20 * 60 * 1000L
	const val MONTH = 31 * DAY
	const val YEAR = 12 * MONTH

	private const val MINUTE = 60_000L
	private const val HOUR = 60 * MINUTE

	data class YearlyEvent(val name: String, val month: Int, val day: Int, val days: Int, val timerId: String)

	val yearly = listOf(
		YearlyEvent("§6Spooky Festival", 7, 29, 3, "spooky-festival"),
		YearlyEvent("§bSeason of Jerry", 11, 24, 8, "season-of-jerry"),
		YearlyEvent("§eNew Year Celebration", 11, 29, 3, "new-year"),
		YearlyEvent("§aTraveling Zoo", 3, 1, 3, "traveling-zoo"),
		YearlyEvent("§aTraveling Zoo", 9, 1, 3, "traveling-zoo"),
	)

	fun nextYearly(event: YearlyEvent, now: Long): Pair<Long, Long> {
		val intoYear = event.month * MONTH + (event.day - 1) * DAY
		val length = event.days * DAY
		var start = EPOCH + Math.floorDiv(now - EPOCH, YEAR) * YEAR + intoYear
		while (start + length <= now) start += YEAR
		return start to start + length
	}

	fun nextHourly(minute: Int, durationMin: Int, now: Long): Pair<Long, Long> {
		val length = durationMin * MINUTE
		val thisHour = now - now % HOUR + minute * MINUTE
		val start = if (thisHour + length > now) thisHour else thisHour + HOUR
		return start to start + length
	}

	internal fun countdown(ms: Long): String {
		val totalSeconds = ms / 1000
		val days = totalSeconds / 86400
		val hours = totalSeconds % 86400 / 3600
		val minutes = totalSeconds % 3600 / 60
		return when {
			days > 0 -> "${days}d ${hours}h"
			hours > 0 -> "${hours}h ${minutes}m"
			else -> "${minutes}m ${totalSeconds % 60}s"
		}
	}

	private class Upcoming(val id: String, val name: String, val start: Long, val end: Long)

	fun entriesAt(now: Long): List<Timers.TimerEntry> {
		val upcoming = buildList {
			nextHourly(15, 20, now).let { (s, e) -> add(Upcoming("jacobs-contest", "§eJacob's Contest", s, e)) }
			nextHourly(55, 5, now).let { (s, e) -> add(Upcoming("dark-auction", "§5Dark Auction", s, e)) }
			for (ev in yearly) nextYearly(ev, now).let { (s, e) -> add(Upcoming(ev.timerId, ev.name, s, e)) }
		}
		return upcoming.sortedBy { it.start }.map {
			val running = it.start <= now
			val status = if (running) "§aactive, ends in " + countdown(it.end - now) else "§7in " + countdown(it.start - now)
			Timers.TimerEntry(it.id, it.name, status, running, maxOf(0L, it.start - now).milliseconds)
		}
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		entriesAt(System.currentTimeMillis()).forEach(event::add)
	}
}
