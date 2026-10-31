// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.kit.terminals

import kotlin.math.abs

object KitTerminalLogic {

	val RUBIX_ORDER = listOf("orange", "yellow", "green", "blue", "red")

	enum class RubixMode { FEWEST_CLICKS, LEFT_CLICKS_ONLY }

	private fun leftClicks(from: Int, to: Int): Int {
		val n = RUBIX_ORDER.size
		return if (to >= from) to - from else n - from + to
	}

	fun rubixClicksFor(goal: Int, panes: Map<Int, Int>, mode: RubixMode): Map<Int, Int> {
		val out = LinkedHashMap<Int, Int>()
		for ((slot, colour) in panes) {
			val left = leftClicks(colour, goal)
			val clicks = if (mode == RubixMode.FEWEST_CLICKS && left > 2) left - RUBIX_ORDER.size else left
			if (clicks != 0) out[slot] = clicks
		}
		return out
	}

	fun rubixSolve(panes: Map<Int, Int>, mode: RubixMode): Map<Int, Int> {
		if (panes.isEmpty()) return emptyMap()
		var best: Map<Int, Int>? = null
		var bestCost = Int.MAX_VALUE
		for (goal in RUBIX_ORDER.indices) {
			val plan = rubixClicksFor(goal, panes, mode)
			var cost = 0
			for (c in plan.values) cost += abs(c)
			if (best == null || cost < bestCost) { best = plan; bestCost = cost }
		}
		return best!!
	}

	fun melodyReady(magentaSlot: Int, limeSlot: Int): Boolean {
		if (magentaSlot < 0 || limeSlot < 0) return false
		return magentaSlot % 9 == limeSlot % 9
	}

	fun firstClickBlocked(openedAt: Long, now: Long, protMs: Int): Boolean = protMs > 0 && now - openedAt < protMs
}
