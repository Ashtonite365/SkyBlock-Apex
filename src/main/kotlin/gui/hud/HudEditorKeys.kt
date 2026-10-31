// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.hud

import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.keybindings.GenericInputButton
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

object HudEditorKeys {
	val ARROWS = setOf(InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_UP, InputConstants.KEY_DOWN)

	fun isArrow(binding: SavedKeyBinding): Boolean =
		(binding.button as? GenericInputButton.KeyCodeButton)?.keyCode in ARROWS

	fun resizeFires(pressed: List<Boolean>, wasDown: List<Boolean>, arrowBound: List<Boolean>): List<Boolean> =
		pressed.indices.map { pressed[it] && !wasDown.getOrElse(it) { false } && !arrowBound[it] }
}
