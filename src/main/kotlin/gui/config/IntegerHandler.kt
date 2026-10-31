package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.data.ManagedConfig

class IntegerHandler(
	val config: ManagedConfig, val min: Int, val max: Int,
) : ManagedConfig.OptionCodec<Int> {
	override fun encode(element: Int): JsonElement? = JsonPrimitive(element)
	override fun decode(element: JsonElement): Int = element.jsonPrimitive.int

	override fun appendRows(
		opt: ManagedOption<Int>, guiAppender: GuiAppender,
	) =
		guiAppender.appendSliderRow(
			label = opt.labelText,
			readout = { ApexFormatters.withThousands(opt.value, 0) },
			min = min.toFloat(), max = max.toFloat(),
			read = { opt.value.toFloat() },
			write = { opt.value = it.toInt() },
		)
}
