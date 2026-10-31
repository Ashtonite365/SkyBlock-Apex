// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

data class WorldKeyboardEvent(val action
	: GenericInputAction, val modifiers: InputModifiers) : ApexEvent.Cancellable() {
	companion object Companion : ApexEventBus<WorldKeyboardEvent>()

	fun matches(keyBinding: SavedKeyBinding, atLeast: Boolean = false): Boolean = keyBinding.matches(action, modifiers, atLeast)
}
