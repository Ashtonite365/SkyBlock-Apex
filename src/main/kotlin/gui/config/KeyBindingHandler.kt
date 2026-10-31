package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.keybindings.ApexKeyBindings
import dev.ashtonite365.apex.keybindings.SavedKeyBinding
import dev.ashtonite365.apex.util.data.ManagedConfig

class KeyBindingHandler(
	val name: String, val managedConfig: ManagedConfig,
) : ManagedConfig.OptionCodec<SavedKeyBinding> {

	override fun onCreated(opt: ManagedOption<SavedKeyBinding>): Unit = ApexKeyBindings.registerKeyBinding(name, opt)

	override fun encode(element: SavedKeyBinding): JsonElement? = Json.encodeToJsonElement(SavedKeyBinding.serializer(), element)
	override fun decode(element: JsonElement): SavedKeyBinding = Json.decodeFromJsonElement(SavedKeyBinding.serializer(), element)

	fun createButtonComponent(opt: ManagedOption<SavedKeyBinding>): ApexButton {
		var built: ApexButton? = null
		val capture = KeyBindingStateManager(
			read = { opt.value },
			write = { binding -> opt.value = binding; opt.element.markDirty() },
			dropFocus = { built?.blur() },
			takeFocus = { built?.requestFocus() },
		)
		return capture.createButton().also { button ->
			built = button
			capture.refreshLabel()
		}
	}

	override fun appendRows(
		opt: ManagedOption<SavedKeyBinding>, guiAppender: GuiAppender,
	) =
		guiAppender.appendLabeledRow(label = opt.labelText, right = createButtonComponent(opt))
}
