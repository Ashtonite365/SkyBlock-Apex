// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.style

object ScreenTransitions {
	const val SWAP_MS = 100L
	private const val LONG_AGO = Long.MIN_VALUE / 4
	@Volatile var lastRemovedNs: Long = LONG_AGO; private set

	fun removed(nowNs: Long = System.nanoTime()) { lastRemovedNs = nowNs }
	fun resetForTests() { lastRemovedNs = LONG_AGO }

	fun isSwap(nowNs: Long, removedNs: Long) = removedNs != LONG_AGO && nowNs - removedNs in 0..SWAP_MS * 1_000_000

	fun openStartNs(nowNs: Long = System.nanoTime()): Long = if (isSwap(nowNs, lastRemovedNs)) nowNs - 60_000_000_000L else nowNs

	fun closeShows(screenOpen: Boolean) = !screenOpen
}
