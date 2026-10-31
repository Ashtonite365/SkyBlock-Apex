// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.events

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.Screen
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent.Companion.internalPollAction
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

sealed interface HandledScreenInputEvent  {
	val screen: Screen
	val input: GenericInputAction
	val modifiers: InputModifiers

	fun matches(keyBinding: SavedKeyBinding, atLeast: Boolean = false): Boolean = keyBinding.matches(input, modifiers, atLeast)
}

data class HandledScreenKeyPressedEvent(override val screen: Screen, override val input: GenericInputAction,
		override val modifiers: InputModifiers) : ApexEvent.Cancellable(), HandledScreenInputEvent {

	val isRepeat: Boolean = InputConstants.REPEAT == internalPollAction()

	fun isLeftClick(): Boolean = GenericInputAction.mouse(InputConstants.MOUSE_BUTTON_LEFT) == input

	companion object Companion : ApexEventBus<HandledScreenKeyPressedEvent>() {

		private var stashedAction: Int = -1

		fun internalPushAction(action: Int) = run { stashedAction = action }

		fun internalPollAction(): Int = stashedAction
	}
}

data class HandledScreenKeyReleasedEvent(override val screen: Screen, override val input: GenericInputAction,
		override val modifiers: InputModifiers) : ApexEvent.Cancellable(), HandledScreenInputEvent {
	companion object Companion : ApexEventBus<HandledScreenKeyReleasedEvent>()
}
