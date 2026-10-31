package dev.ashtonite365.apex.keybindings

import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

@Serializable
data class SavedKeyBinding(val button: GenericInputButton, val modifiers: InputModifiers) {
	companion object {
		fun isShiftDown(): Boolean = InputModifiers.current().shift

		fun withoutMods(input: GenericInputButton) = SavedKeyBinding(button = input, modifiers = InputModifiers.none())
		fun unbound(): SavedKeyBinding = withoutMods(GenericInputButton.Unbound)
		fun keyWithMods(keyCode: Int, mods: InputModifiers) = SavedKeyBinding(GenericInputButton.ofKeyCode(keyCode), mods)
		fun keyWithoutMods(keyCode: Int): SavedKeyBinding = keyWithMods(keyCode, InputModifiers.none())
	}

	val isBound: Boolean get() = button.isBound()

	fun isPressed(atLeast: Boolean = false): Boolean =
		button.isPressed() && InputModifiers.current().without(InputModifiers.ofKey(button)).matches(modifiers, atLeast)

	fun matches(action: GenericInputAction, inputModifiers: InputModifiers, atLeast: Boolean = false): Boolean =
		action.matches(button) && modifiers.matches(inputModifiers, atLeast)

	fun format(): Component {
		val label: MutableComponent = Component.empty()
		if (!modifiers.isEmpty()) label.append(modifiers.format()).append(" + ")
		return label.append(button.format())
	}

	override fun toString(): String = format().string
}
