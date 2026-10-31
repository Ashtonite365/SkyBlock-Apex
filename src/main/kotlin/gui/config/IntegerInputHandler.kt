// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.gui.component.TextFieldComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.util.data.ManagedConfig

class IntegerInputHandler(val config: ManagedConfig, val min: Int, val max: Int) : ManagedConfig.OptionCodec<Int> {
	override fun encode(element: Int): JsonElement? = JsonPrimitive(element)
	override fun decode(element: JsonElement): Int = element.jsonPrimitive.int.coerceIn(min, max)

	fun parse(text: String): Int? = parse(text, min, max)

	companion object {

		fun parse(text: String, min: Int, max: Int): Int? = text.trim().toIntOrNull()?.takeIf { it in min..max }
	}

	override fun appendRows(
		opt: ManagedOption<Int>, guiAppender: GuiAppender,
	) {
		var typed = opt.value.toString()
		val text = object : GetSetter<String> {
			override fun get(): String = typed
			override fun set(newValue: String) {
				typed = newValue.filter(Char::isDigit).take(6)
				val accepted = parse(typed) ?: return
				opt.set(accepted)
				config.markDirty()
			}
		}
		guiAppender.appendLabeledRow(opt.labelText, TextFieldComponent(text, 60, suggestion = "$min-$max"))
	}
}
