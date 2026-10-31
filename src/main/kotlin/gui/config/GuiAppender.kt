package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.common.IMinecraft
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent
import io.github.notenoughupdates.moulconfig.gui.component.SliderComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.gui.FixedComponent

class GuiAppender(
	val width: Int, val screenAccessor: () -> Screen,
) {
	val panel: MutableList<GuiComponent> = ArrayList()
	internal val reloadables: MutableList<() -> Unit> = ArrayList()

	fun onReload(reloadable: () -> Unit): Unit { reloadables += reloadable }

	fun appendFullRow(widget: GuiComponent): Unit { panel += widget }

	fun appendSplitRow(left: GuiComponent, right: GuiComponent) =
		appendFullRow(RowComponent(halfWidth(left), halfWidth(right)))

	fun appendLabeledRow(label: Component, right: GuiComponent) =
		appendSplitRow(TextComponent(label.string), right)

	private fun halfWidth(content: GuiComponent): GuiComponent =
		FixedComponent(GetSetter.constant(width / 2), null, content)

	internal fun appendSliderRow(
		label: Component,
		readout: () -> String,
		min: Float, max: Float,
		read: () -> Float,
		write: (Float) -> Unit,
	) {
		val text = TextComponent(
			IMinecraft.INSTANCE.defaultFontRenderer,
			{ StructuredText.of(readout()) },
			40, TextComponent.TextAlignment.CENTER, true, false,
		)
		val slider = SliderComponent(
			object : GetSetter<Float> {
				override fun get(): Float = read()
				override fun set(newValue: Float): Unit = write(newValue)
			},
			min, max, 0.1F, 130,
		)
		appendLabeledRow(label, RowComponent(text, slider))
	}
}
