package dev.ashtonite365.apex.gui.config

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.data.ManagedConfig

class DurationHandler(
	val config: ManagedConfig, val min: Duration, val max: Duration,
) : ManagedConfig.OptionCodec<Duration> {
	override fun encode(element: Duration): JsonElement? = JsonPrimitive(element.inWholeMilliseconds)
	override fun decode(element: JsonElement): Duration = element.jsonPrimitive.long.milliseconds

	private fun Duration.asSeconds(): Float = toDouble(DurationUnit.SECONDS).toFloat()

	override fun appendRows(
		opt: ManagedOption<Duration>, guiAppender: GuiAppender,
	) =
		guiAppender.appendSliderRow(
			label = opt.labelText,
			readout = { ApexFormatters.timespanText(opt.value) },
			min = min.asSeconds(), max = max.asSeconds(),
			read = { opt.value.asSeconds() },
			write = { opt.value = it.toDouble().seconds },
		)
}
