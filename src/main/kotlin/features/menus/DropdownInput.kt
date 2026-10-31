// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import net.minecraft.client.gui.screens.Screen

object DropdownInput {

	fun interface Claimer { fun press(screen: Screen, mx: Double, my: Double, button: Int): Boolean }

	private val claimers = java.util.concurrent.CopyOnWriteArrayList<Claimer>()
	private val swallowed = HashSet<Int>()

	fun register(c: Claimer) { if (c !in claimers) claimers += c }

	@JvmStatic
	fun press(screen: Screen?, mx: Double, my: Double, button: Int): Boolean {
		if (screen == null) return false
		val taken = claimers.any { runCatching { it.press(screen, mx, my, button) }.getOrDefault(false) }
		if (taken) swallowed += button
		return taken
	}

	@JvmStatic
	fun release(button: Int): Boolean = swallowed.remove(button)

	fun resetForTests() { claimers.clear(); swallowed.clear() }

	data class Outcome(val consumed: Boolean, val open: Boolean, val pick: Int?)

	fun decide(open: Boolean, head: IntArray, rows: List<IntArray>, mx: Double, my: Double, button: Int, leftButton: Int = 0): Outcome {
		fun inside(r: IntArray) = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]
		if (open) {
			val hit = rows.indexOfFirst { inside(it) }.takeIf { it >= 0 && button == leftButton }
			return Outcome(true, false, hit)
		}
		if (button == leftButton && inside(head)) return Outcome(true, true, null)
		return Outcome(false, false, null)
	}
}
