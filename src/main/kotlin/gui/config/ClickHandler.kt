package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import kotlinx.serialization.json.JsonElement
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.util.data.ManagedConfig

class ClickHandler(val config: ManagedConfig, val runnable: () -> Unit) :
	ManagedConfig.OptionCodec<Unit>, ActionCodec {
	override fun runAction() = runnable()
	override fun encode(element: Unit): JsonElement? = null
	override fun decode(element: JsonElement): Unit = Unit

	override fun appendRows(
		opt: ManagedOption<Unit>, guiAppender: GuiAppender,
	) {
		val caption = TextComponent(opt.labelText.string)
		guiAppender.appendLabeledRow(opt.labelText, ApexButton(caption, action = runnable))
	}

	companion object {

		fun isNoRunnable(runnableId: Int) = runnableId < 0
	}
}
