// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.golden

import dev.ashtonite365.apex.features.calendar.CalendarScreen
import dev.ashtonite365.apex.features.calendar.SkyBlockEventTimers
import dev.ashtonite365.apex.features.timers.BoosterCookieLogic
import dev.ashtonite365.apex.features.timers.BoosterCookieLogic.Status
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class Batch5GoldenTest {
	private fun enc(s: Status): String = when (s) {
		Status.Unknown -> "U"
		Status.Inactive -> "I"
		is Status.Active -> "A:${s.text}:${s.remaining?.inWholeMilliseconds ?: "-"}"
	}

	private fun dec(s: String): Status = when (s) {
		"U" -> Status.Unknown
		"I" -> Status.Inactive
		else -> s.split(':').let { (_, t, m) -> Status.Active(t, m.toLongOrNull()?.minutes) }
	}

	private fun lines(arg: String) = if (arg.isEmpty()) emptyList() else arg.split('¦')

	@Test
	fun timers() {
		val out = Golden.load("batch5-timers").map { (k, _) ->
			val p = k.split('|')
			k to Golden.run {
				when (p[0]) {
					"parse" -> BoosterCookieLogic.parseRemaining(k.substringAfter('|'))?.inWholeMilliseconds?.toString() ?: "null"
					"tab" -> enc(BoosterCookieLogic.readStatus(lines(k.substringAfter('|'))))
					"lore" -> enc(BoosterCookieLogic.readLore(lines(k.substringAfter('|'))))
					"combine" -> enc(BoosterCookieLogic.combine(dec(p[1]), dec(p[2])))
					"line" -> BoosterCookieLogic.statusLine(dec(p[1])) ?: "null"
					"pending" -> BoosterCookieLogic.isPending(dec(p[1]), p[2].toInt().minutes).toString()
					"warn" -> {
						val w = BoosterCookieLogic.Warner()
						k.substringAfter('|').split('¦').joinToString(",") { step ->
							val s = step.split('|')
							if (s.size > 4) w.reset()
							w.update(dec(s[0]), s[1].toInt().minutes, s[2].toBoolean(), s[3].toBoolean())?.name ?: "-"
						}
					}
					"fmt" -> SkyBlockEventTimers.countdown(p[1].toLong())
					"yearly" -> SkyBlockEventTimers.nextYearly(SkyBlockEventTimers.yearly[p[1].toInt()], p[2].toLong()).toString()
					"hourly" -> SkyBlockEventTimers.nextHourly(p[1].toInt(), p[2].toInt(), p[3].toLong()).toString()
					"rows" -> SkyBlockEventTimers.entriesAt(p[1].toLong()).joinToString("\n") {
						"${it.id}|${it.label}|${it.status}|${it.pending}|${it.remaining?.inWholeMilliseconds}"
					}
					"events" -> CalendarScreen.eventsOn(p[1].toInt(), p[2].toInt()).joinToString(";")
					"months" -> CalendarScreen.MONTHS.joinToString(";")
					else -> error(k)
				}
			}
		}
		Golden.verify("batch5-timers", out, minCases = 3000)
	}
}
