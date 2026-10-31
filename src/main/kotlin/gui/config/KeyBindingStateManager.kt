package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.deps.libninepatch.NinePatch
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.gui.KeyboardEvent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.platform.MoulConfigPlatform
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.keybindings.GenericInputButton
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.TextInputFocus

class KeyBindingStateManager(
	private val read: () -> SavedKeyBinding,
	private val write: (SavedKeyBinding) -> Unit,
	private val dropFocus: () -> Unit,
	private val takeFocus: () -> Unit,
) {

	var capturing = false

	var pendingModifier: GenericInputButton? = null

	var label: Component = Component.empty()

	fun onClick(mouseButton: Int) {
		if (capturing) {
			handleInput(GenericInputButton.mouse(mouseButton), true)
			return
		}
		if (mouseButton != LEFT_BUTTON) return
		capturing = true
		pendingModifier = null
		takeFocus()
		refreshLabel()
	}

	fun handleInput(button: GenericInputButton, down: Boolean): Boolean {
		val held = InputModifiers.current()
		return if (down) keyDown(button, held) else keyUp(button, held)
	}

	fun keyDown(button: GenericInputButton, mods: InputModifiers): Boolean {
		if (!capturing) return false
		when {
			button == GenericInputButton.escape() -> settle(SavedKeyBinding.unbound())
			button.isModifier() -> {
				pendingModifier = button
				refreshLabel()
			}
			else -> settle(SavedKeyBinding(button, mods))
		}
		return true
	}

	fun keyUp(button: GenericInputButton, mods: InputModifiers): Boolean {
		if (!capturing) return false
		if (button == pendingModifier) settle(SavedKeyBinding(button, mods.without(InputModifiers.ofKey(button))))
		return true
	}

	fun focusLost() {
		capturing = false
		pendingModifier = null
		refreshLabel()
	}

	fun refreshLabel() {
		label = if (!capturing) read().format() else waitingText()
	}

	private fun settle(result: SavedKeyBinding) {
		write(result)
		capturing = false
		pendingModifier = null
		dropFocus()
		refreshLabel()
	}

	private fun waitingText(): Component {
		var flags = InputModifiers.current().modifiers
		pendingModifier?.let { flags = flags or InputModifiers.ofKey(it).modifiers }
		val shown = InputModifiers.of(flags)
		val text = Component.empty()
		if (!shown.isEmpty()) text.append(shown.format()).append(" + ")
		text.append(WAITING)
		return text.withStyle(ChatFormatting.YELLOW)
	}

	fun createButton(): ApexButton {
		val owner = this
		val text = TextComponent(
			IMinecraft.INSTANCE.defaultFontRenderer,
			{ MoulConfigPlatform.wrap(owner.label) },
			LABEL_WIDTH, TextComponent.TextAlignment.CENTER, true, false,
		)
		return object : ApexButton(text, action = { owner.onClick(it) }), TextInputFocus {

			override val textInputFocused get() = owner.capturing

			override fun keyboardEvent(event: KeyboardEvent, context: GuiImmediateContext): Boolean {
				if (!owner.capturing || event !is KeyboardEvent.KeyPressed) return super.keyboardEvent(event, context)
				val button = GenericInputButton.ofKeyAndScan(event.keycode, event.scancode)
				owner.handleInput(button, event.pressed)
				return true
			}

			override fun skinFor(context: GuiImmediateContext): NinePatch<MyResourceLocation> =
				if (owner.capturing) skinActive else super.skinFor(context)

			override fun onLostFocus() {
				super.onLostFocus()
				owner.focusLost()
			}
		}
	}

	private companion object {
		const val LEFT_BUTTON = 1
		const val LABEL_WIDTH = 80
		const val WAITING = "???"
	}
}
