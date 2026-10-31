/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): current mining event and Sky Mall perk on the shared Timers HUD.
 * Idea 's mining event display and Sky Mall reminder (research-mods-A #17); written from scratch,
 * no code copied. Display only; reads chat and the tab list.
 */
package dev.ashtonite365.apex.features.mining

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.bareText

object MiningEventTimers {
	val identifier: String
		get() = "mining-event-timers"

	private const val SKYBLOCK_EPOCH = 1560275700000L
	private val SKYBLOCK_DAY = 20.minutes
	private val buffRegex = "New buff: (.+?)\\.?".toRegex()
	private var skyMallPerk: String? = null
	private var skyMallUntil = 0L

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val m = buffRegex.matchEntire(event.bareText.trim()) ?: return
		skyMallPerk = m.groupValues[1]
		val now = System.currentTimeMillis()
		val day = SKYBLOCK_DAY.inWholeMilliseconds
		skyMallUntil = SKYBLOCK_EPOCH + ((now - SKYBLOCK_EPOCH) / day + 1) * day
	}

	private val endsIn = "Ends In: (?:(\\d+)m)? ?(?:(\\d+)s)?".toRegex()

	private fun tabEvent(): Pair<String, Duration?>? {
		val lines = TabListReader.currentSnapshot.body.map { it.bareText.trim() }
		val idx = lines.indexOfFirst { it.startsWith("Event: ") }
		if (idx < 0) return null
		val name = lines[idx].removePrefix("Event: ")
		val left = lines.drop(idx + 1).take(3).firstNotNullOfOrNull { endsIn.find(it) }?.let {
			(it.groupValues[1].toIntOrNull() ?: 0).minutes + (it.groupValues[2].toIntOrNull() ?: 0).seconds
		}
		return name to left
	}

	@Subscribe
	fun onCollectTimers(event: Timers.TimerCollectEvent) {
		skyMallPerk?.let { perk ->
			val left = (skyMallUntil - System.currentTimeMillis()).milliseconds
			if (left.isPositive())
				event.add(Timers.TimerEntry("sky-mall", "Sky Mall", "§b$perk §7(${dev.ashtonite365.apex.util.ApexFormatters.timespanText(left)})", false, left))
			else skyMallPerk = null
		}
		tabEvent()?.let { (name, left) ->
			event.add(Timers.TimerEntry("mining-event", "Mining Event", "§d$name" + (left?.let { " §7(${dev.ashtonite365.apex.util.ApexFormatters.timespanText(it)})" } ?: ""), false, left))
		}
	}
}
