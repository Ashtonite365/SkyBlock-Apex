// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.util.data.ManagedConfig

class StringSetHandler(val config: ManagedConfig) : ManagedConfig.OptionCodec<Set<String>>, ActionCodec {
	private var option: ManagedOption<Set<String>>? = null

	override fun onCreated(opt: ManagedOption<Set<String>>) { option = opt }

	override fun encode(element: Set<String>): JsonElement? = JsonArray(element.sorted().map(::JsonPrimitive))
	override fun decode(element: JsonElement): Set<String> =
		element.jsonArray.mapTo(LinkedHashSet()) { it.jsonPrimitive.content }

	override fun runAction() {
		val opt = option ?: return
		if (opt.value.isNotEmpty()) opt.value = emptySet()
	}

	override fun appendRows(opt: ManagedOption<Set<String>>, guiAppender: GuiAppender) {
		guiAppender.appendLabeledRow(opt.labelText, ApexButton(TextComponent(opt.labelText.string), action = ::runAction))
	}
}
