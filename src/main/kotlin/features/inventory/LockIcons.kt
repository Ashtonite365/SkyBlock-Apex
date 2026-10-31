// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.inventory

import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleState

object LockIcons {
	const val SIZE = 6
	const val INSET = 1

	data class Placement(val x: Int, val y: Int, val size: Int)

	fun styleFor(state: StyleState): Style = if (!state.wrapAll || !state.customMenus) Style.SKYGLASS else state.style

	fun sprite(kind: SlotLinkOverlay.LockKind, state: StyleState): String = "apex:${kind.base}_${styleFor(state).id}"

	fun place(x: Int, y: Int, slot: Int = 16): Placement {
		val size = maxOf(3, (slot * SIZE + 8) / 16)
		val inset = maxOf(1, slot * INSET / 16)
		return Placement(x + inset, y + slot - inset - size, size)
	}
}
