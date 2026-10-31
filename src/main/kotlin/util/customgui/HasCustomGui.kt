// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.customgui

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

@Suppress("FunctionName")
interface HasCustomGui {
	fun getCustomGui_Apex(): CustomGui?
	fun setCustomGui_Apex(gui: CustomGui?)
}

var <T : AbstractContainerScreen<*>> T.customGui: CustomGui?
	get() = host.getCustomGui_Apex()
	set(gui) = host.setCustomGui_Apex(gui)

private val AbstractContainerScreen<*>.host: HasCustomGui get() = this as HasCustomGui
