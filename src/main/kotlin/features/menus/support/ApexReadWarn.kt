// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

class ApexReadWarn(private val menu: String, private val graceMs: Long = 1500L, private val debounceMs: Long = 30_000L) {
	private var ok = true
	private var filled = 0
	private var changedAt = 0L
	private var warnedWindow: Int? = null
	private var lastWarn = Long.MIN_VALUE

	fun parsed(readable: Boolean, filledSlots: Int, nowMs: Long) { ok = readable; filled = filledSlots; changedAt = nowMs }

	fun check(windowId: Int, title: String, nowMs: Long): String? {
		if (ok || filled <= 0 || nowMs - changedAt <= graceMs) return null
		if (warnedWindow == windowId) return null
		if (lastWarn != Long.MIN_VALUE && nowMs - lastWarn < debounceMs) { warnedWindow = windowId; return null }
		warnedWindow = windowId; lastWarn = nowMs
		return "[Apex] $menu: could not read the page '$title' ($filled items); leaving the vanilla chest"
	}
}
