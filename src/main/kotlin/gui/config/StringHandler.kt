package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.data.ManagedConfig

class StringHandler(val config: ManagedConfig) :
	ManagedConfig.OptionCodec<String> {
	override fun encode(element: String): JsonElement? = JsonPrimitive(element)
	override fun decode(element: JsonElement): String = element.jsonPrimitive.content

	override fun appendRows(
		opt: ManagedOption<String>, guiAppender: GuiAppender,
	) {
		val hint = Component.translatableWithFallback("${opt.rawLabelText}.hint", "").string
		val field = TextFieldComponent(opt.alsoMarking(config), 130, suggestion = hint)
		guiAppender.appendLabeledRow(opt.labelText, field)
	}
}
