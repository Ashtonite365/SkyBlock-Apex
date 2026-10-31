// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.auction

object AhHoverLink {

	@Volatile var panelSlot: Int? = null

	@Volatile var gridSlot: Int? = null

	fun cardHighlighted(cardSlot: Int, mouseOnCard: Boolean, panelSlot: Int?) = mouseOnCard || cardSlot == panelSlot

	fun rowFor(rowSlots: List<Int>, apexGridSlot: Int?, vanillaSlot: Int?): Int =
		(apexGridSlot ?: vanillaSlot)?.let { rowSlots.indexOf(it) } ?: -1
}
