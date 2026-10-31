// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.inventory.storageoverlay

object StorageBackArrow {
	const val SIZE = 18
	const val GAP = 4

	enum class Action { GO_BACK, CLOSE }

	fun rect(frameX: Int, frameY: Int): IntArray = intArrayOf(frameX - SIZE - GAP, frameY + GAP, SIZE, SIZE)

	fun hit(frameX: Int, frameY: Int, mx: Double, my: Double): Boolean =
		rect(frameX, frameY).let { StorageClickLogic.inside(it[0], it[1], it[2], it[3], mx, my) }

	fun decide(items: List<StorageBackChain.Item?>?): Action =
		if (items != null && StorageBackChain.findBack(items) != null) Action.GO_BACK else Action.CLOSE

	fun press(items: List<StorageBackChain.Item?>?, clickSlot: (Int) -> Unit, close: () -> Unit): Action {
		val slot = items?.let { StorageBackChain.findBack(it) }
		return if (slot != null) { clickSlot(slot); Action.GO_BACK } else { close(); Action.CLOSE }
	}
}
