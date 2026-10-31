package dev.ashtonite365.apex.util.tracker

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.TimeMark

class SessionTracker(
	val idleTimeout: Duration = 1.minutes,
	val resetAfter: Duration? = 10.minutes,
) {
	val counters = linkedMapOf<String, Double>()
	var activeMillis = 0L
		private set
	private var lastActivity: TimeMark? = null

	val isEmpty get() = counters.isEmpty()

	fun reset() {
		counters.clear()
		activeMillis = 0L
		lastActivity = null
	}

	fun add(counter: String, amount: Double = 1.0) {
		val last = lastActivity
		if (last != null) {
			val gap = last.passedTime()
			if (resetAfter != null && gap > resetAfter) reset()
			else if (gap <= idleTimeout) activeMillis += gap.inWholeMilliseconds
		}
		lastActivity = TimeMark.now()
		counters.merge(counter, amount, Double::plus)
	}

	fun get(counter: String) = counters[counter] ?: 0.0

	fun perHour(counter: String): Double? {
		val hours = activeMillis / 3_600_000.0
		if (hours < 0.005) return null
		return get(counter) / hours
	}

	fun sinceLastActivity(): Duration? = lastActivity?.passedTime()

	fun lines(labelColour: String = "§3", valueColour: String = "§a"): List<Component> =
		counters.keys.map { name ->
			val rate = perHour(name)?.let { " §7(${ApexFormatters.withThousands(it.toLong())}/h)" } ?: ""
			Component.literal("$labelColour$name: $valueColour${ApexFormatters.withThousands(get(name).toLong())}$rate")
		}
}
