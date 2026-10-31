// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import com.mojang.blaze3d.platform.InputConstants

object MenuButtons {
	fun packetButton(inputButton: Int): Int? = when (inputButton) {
		InputConstants.MOUSE_BUTTON_LEFT -> 0
		InputConstants.MOUSE_BUTTON_RIGHT -> 1
		else -> null
	}

	fun isLeft(inputButton: Int) = inputButton == InputConstants.MOUSE_BUTTON_LEFT

	fun isRight(inputButton: Int) = inputButton == InputConstants.MOUSE_BUTTON_RIGHT
}
