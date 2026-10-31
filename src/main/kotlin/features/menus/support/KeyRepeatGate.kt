// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.menus.support

class KeyRepeatGate {
	private var held: Int? = null

	fun press(key: Int): Boolean {
		if (held == key) return false
		held = key
		return true
	}

	fun frame(isDown: (Int) -> Boolean) { held?.let { if (!isDown(it)) held = null } }
}
