package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.gui.component.CenterComponent
import io.github.notenoughupdates.moulconfig.gui.component.SwitchComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.util.data.ManagedConfig

class BooleanHandler(val config: ManagedConfig) :
	ManagedConfig.OptionCodec<Boolean> {
	override fun encode(element: Boolean): JsonElement? = JsonPrimitive(element)
	override fun decode(element: JsonElement): Boolean = element.jsonPrimitive.boolean

	override fun appendRows(
		opt: ManagedOption<Boolean>, guiAppender: GuiAppender,
	) {
		val savingState = opt.alsoMarking(config)
		guiAppender.appendLabeledRow(opt.labelText, CenterComponent(SwitchComponent(savingState, 200)))
	}
}

internal fun <T : Any> ManagedOption<T>.alsoMarking(config: ManagedConfig): GetSetter<T> {
	val option = this
	return object : GetSetter<T> {
		override fun get(): T = option.get()
		override fun set(newValue: T) {
			option.set(newValue)
			config.markDirty()
		}
	}
}
