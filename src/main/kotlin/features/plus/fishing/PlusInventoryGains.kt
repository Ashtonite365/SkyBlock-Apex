// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

class PlusInventoryGains {
	private var baseline: Map<String, Long>? = null

	fun reset() { baseline = null }

	fun poll(): Map<String, Long> {
		val player = Client.player
		if (player == null || Client.screen != null) { baseline = null; return emptyMap() }
		val counts = HashMap<String, Long>()
		val inventory = player.inventory
		for (slot in 0 until inventory.containerSize) {
			val stack = inventory.getItem(slot)
			if (stack.isEmpty) continue
			val id = stack.view().repoItemId?.skyblockId ?: continue
			counts.merge(id, stack.count.toLong(), Long::plus)
		}
		val previous = baseline
		baseline = counts
		return previous?.let { gains(it, counts) } ?: emptyMap()
	}

	companion object {

		fun gains(prev: Map<String, Long>, now: Map<String, Long>): Map<String, Long> {
			val out = LinkedHashMap<String, Long>()
			for ((id, count) in now) {
				val delta = count - prev.getOrDefault(id, 0L)
				if (delta > 0) out[id] = delta
			}
			return out
		}
	}
}
