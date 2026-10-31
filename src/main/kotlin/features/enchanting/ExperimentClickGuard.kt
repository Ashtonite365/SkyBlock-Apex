
package dev.ashtonite365.apex.features.enchanting

object ExperimentClickGuard {

	const val DEFAULT_ENABLED = false

	fun shouldBlock(enabled: Boolean, allowed: Set<Int>?, index: Int, bypass: Boolean = false): Boolean =
		enabled && !bypass && index >= 0 && !allowed.isNullOrEmpty() && index !in allowed

	fun sequencerAllowed(order: Map<Int, Int>, replayIndex: Int, replaying: Boolean): Set<Int>? {
		if (!replaying) return null
		return order[replayIndex]?.let { setOf(it) }
	}

	fun <K> superpairsAllowed(hidden: Set<Int>, remembered: Map<Int, K>): Set<Int>? =
		hidden.filter { slot ->
			val key = remembered[slot] ?: return@filter true
			remembered.values.count { it == key } >= 2
		}.toSet().ifEmpty { null }
}
