// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.util.accessors

import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

fun AbstractContainerScreen<*>.getProperRectangle(): Rectangle = castAccessor().let { box -> Rectangle(box.getX_Apex(), box.getY_Apex(), box.getBackgroundWidth_Apex(), box.getBackgroundHeight_Apex()) }
