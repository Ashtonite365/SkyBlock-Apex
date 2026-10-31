// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

class ChestOpenings(private val windowMs: Long = 1500) {
	private val opened = HashSet<Long>()
	private var lastClick: Long? = null
	private var clickAt = Long.MIN_VALUE / 2

	fun isOpened(pos: Long) = pos in opened

	fun onLidOpen(pos: Long) { opened += pos }

	fun onChestClick(pos: Long, nowMs: Long) { lastClick = pos; clickAt = nowMs }

	fun onLocked() { lastClick = null }

	fun onChestScreen(nowMs: Long): Long? {
		val p = lastClick ?: return null
		if (nowMs - clickAt > windowMs) return null
		lastClick = null
		opened += p
		return p
	}

	fun reset() { opened.clear(); lastClick = null }
}

object OpenedChests {
	val state = ChestOpenings()
}
