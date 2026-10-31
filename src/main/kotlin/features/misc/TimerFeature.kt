// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.misc

import com.mojang.brigadier.arguments.IntegerArgumentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.DurationArgumentType
import dev.ashtonite365.apex.commands.RestArgumentType
import dev.ashtonite365.apex.commands.get
import dev.ashtonite365.apex.commands.onArgument
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.CommonSoundEffects
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.MinecraftDispatcher
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.runsOnClick
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.lime
import dev.ashtonite365.apex.util.red
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.yellow

object TimerFeature {
	data class Timer(val start: TimeMark, val duration: Duration, val message: String, val timerId: Int) {
		fun isDone(): Boolean = start.passedTime() >= duration
		fun timeLeft(): Duration = maxOf(duration - start.passedTime(), Duration.ZERO)
	}

	val timers: MutableList<Timer> = mutableListOf()

	var createTimerId: Int = 0

	private fun span(d: Duration) = ApexFormatters.timespanText(d)

	private fun announce(text: Component) = Client.showChat(text)

	private fun ring(timer: Timer) {
		announce(tr("apex.timer.finished", "The timer you set ${span(timer.duration)} ago just went off: ${timer.message}").yellow())

		Apex.coroutineScope.launch { withContext(MinecraftDispatcher) { repeat(5) { CommonSoundEffects.playSuccess(); delay(200.milliseconds) } } }
	}

	@Subscribe
	fun tick(event: TickEvent) {
		val finished = timers.filter(Timer::isDone)
		finished.forEach(::ring)
		timers -= finished.toSet()
	}

	fun startTimer(duration: Duration, message: String) {
		val id = createTimerId
		createTimerId = id + 1
		timers += Timer(TimeMark.now(), duration, message, id)
		val cancel = tr("apex.timer.cancelbutton", "Click here to cancel the timer.").runsOnClick("/apex timer clear $id").red()
		announce(tr("apex.timer.start", "Timer started for $message in ${span(duration)}.").lime().append(" ").append(cancel))
	}

	fun clearTimer(timerId: Int) {
		val timer = timers.firstOrNull { it.timerId == timerId }
		if (timer == null) {
			announce(tr("apex.timer.cancel.fail", "Could not cancel that timer. Maybe it was already cancelled?").red())
			return
		}
		timers.remove(timer)
		announce(tr("apex.timer.cancel.done", "Cancelled timer ${timer.message}. It would have been done in ${span(timer.timeLeft())}.").lime())
	}

	@Subscribe
	fun onCommands(event: CommandEvent.SubCommand) {
		event.addSubcommand("cleartimer", ManagedConfig.Category.TIMERS) {
			onArgument("timerId", IntegerArgumentType.integer(0)) { id -> onRun { clearTimer(this[id]) } }
			onRun { timers.map(Timer::timerId).forEach(::clearTimer) }
		}
		event.addSubcommand("timer", ManagedConfig.Category.TIMERS) {
			onArgument("time", DurationArgumentType) { time ->
				onRun { startTimer(this[time], "no message") }
				onArgument("message", RestArgumentType) { text -> onRun { startTimer(this[time], this[text]) } }
			}
		}
	}
}
