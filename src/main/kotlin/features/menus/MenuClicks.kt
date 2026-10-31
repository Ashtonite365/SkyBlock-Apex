// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus

import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.util.Client

object MenuClicks {

	fun click(menu: AbstractContainerMenu, slot: Slot, mouse: Int = 0) = ApexClickDelay.send(menu, slot, mouse)

	fun clickLater(menu: AbstractContainerMenu, slot: Slot, mouse: Int = 0) = ApexClickDelay.clickLater(menu, slot, mouse, click = ::autoClick)

	fun autoClick(menu: AbstractContainerMenu, slot: Slot, mouse: Int) = ApexClickDelay.send(menu, slot, mouse)
}

class ContainerGate(baseline: Int) {
	var handled = baseline; private set
	private var seen = baseline
	private var seenAtMs = 0L
	companion object { const val GRACE_MS = 1500L }

	fun isNew(containerId: Int, nowMs: Long): Boolean {
		if (containerId == handled) return false
		if (containerId != seen) { seen = containerId; seenAtMs = nowMs }
		return true
	}

	fun stillPopulating(nowMs: Long) = nowMs - seenAtMs < GRACE_MS

	fun done(containerId: Int) { handled = containerId }
}

class ContainerSettle {
	private var id = Int.MIN_VALUE
	private var last: String? = null
	fun stable(containerId: Int, signature: String): Boolean {
		if (containerId != id) { id = containerId; last = signature; return false }
		if (signature != last) { last = signature; return false }
		return true
	}
}
