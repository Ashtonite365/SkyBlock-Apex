// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.kit

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ApexEvent
import dev.ashtonite365.apex.events.ApexEventBus
import dev.ashtonite365.apex.events.WorldReadyEvent

data class ServerTickEvent(val tick: Long) : ApexEvent() {
	companion object : ApexEventBus<ServerTickEvent>()
}

object ServerTicks {

	@Volatile
	var totalTicks: Long = 0L
		private set
	private var seenId = false
	private var previousId = 0

	fun accept(pingId: Int): Boolean {
		val repeat = seenId && previousId == pingId
		seenId = true
		previousId = pingId
		if (!repeat) totalTicks += 1
		return !repeat
	}

	fun reset() {
		totalTicks = 0L
		seenId = false
	}

	fun since(startTick: Long): Long = maxOf(0L, totalTicks - startTick)

	@JvmStatic
	fun onPing(pingId: Int) {
		if (accept(pingId)) ServerTickEvent.publish(ServerTickEvent(totalTicks))
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = reset()
}
