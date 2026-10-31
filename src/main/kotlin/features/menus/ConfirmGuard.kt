// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

class ConfirmGuard<T : Any>(var confirmMs: Long = 3_000L, val timeoutMs: Long = 15_000L) {
	var pending: T? = null; private set
	private var atMs = 0L

	fun hold(value: T, nowMs: Long = System.currentTimeMillis()) { pending = value; atMs = nowMs }
	val openedAtMs get() = atMs

	fun secondsLeft(nowMs: Long = System.currentTimeMillis()): Long {
		if (pending == null) return 0
		val left = atMs + confirmMs - nowMs
		return if (left <= 0) 0 else (left + 999) / 1000
	}

	fun proceed(nowMs: Long = System.currentTimeMillis()): T? =
		if (pending == null || secondsLeft(nowMs) > 0) null else pending.also { pending = null }

	fun cancel() { pending = null }

	fun expire(nowMs: Long = System.currentTimeMillis()): Boolean {
		if (pending == null || nowMs - atMs < timeoutMs) return false
		pending = null; return true
	}
}
