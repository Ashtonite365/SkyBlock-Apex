// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.customgui

import net.minecraft.world.inventory.Slot

@Suppress("FunctionName")
interface CoordRememberingSlot {
	fun rememberCoords_apex()
	fun restoreCoords_apex()
	fun getOriginalX_apex(): Int
	fun getOriginalY_apex(): Int
}

val Slot.originalX: Int get() = (this as CoordRememberingSlot).getOriginalX_apex()
val Slot.originalY: Int get() = (this as CoordRememberingSlot).getOriginalY_apex()
