// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.gui.config

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.util.data.ManagedConfig

class DecimalHandler(
	val config: ManagedConfig, val min: Double, val max: Double, val step: Double,
) : ManagedConfig.OptionCodec<Double> {
	init {
		require(min < max && step > 0.0) { "Bad decimal range $min..$max step $step" }
	}

	fun snap(value: Double): Double {
		if (value.isNaN()) return min
		val steps = ((value.coerceIn(min, max) - min) / step).roundToLong()

		return (Math.round((min + steps * step) * 1e6) / 1e6).coerceIn(min, max)
	}

	fun label(value: Double): String {
		val digits = step.toBigDecimal().stripTrailingZeros().scale().coerceAtLeast(0)
		return "%.${digits}f".format(java.util.Locale.ROOT, value)
	}

	override fun encode(element: Double): JsonElement? = JsonPrimitive(snap(element))
	override fun decode(element: JsonElement): Double = snap(element.jsonPrimitive.double)

	override fun appendRows(opt: ManagedOption<Double>, guiAppender: GuiAppender) =
		guiAppender.appendSliderRow(
			label = opt.labelText,
			readout = { label(opt.value) },
			min = min.toFloat(), max = max.toFloat(),
			read = { opt.value.toFloat() },
			write = { opt.value = snap(it.toDouble()) },
		)
}
