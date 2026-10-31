package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.ApexButton
import dev.ashtonite365.apex.gui.hud.editor.HudEditors
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.ManagedConfig

class HudMetaHandler(
	val owner: ManagedConfig,
	val optionId: String,

	val title: MutableComponent,
	val defaultWidth: Int,
	val defaultHeight: Int,
) : ManagedConfig.OptionCodec<HudMeta> {

	override fun encode(element: HudMeta): JsonElement? =
		Json.encodeToJsonElement(HudPosition.serializer(), element.position)

	override fun decode(element: JsonElement) = HudMeta(
		Json.decodeFromJsonElement(HudPosition.serializer(), element),
		Apex.identifier(optionId), title, defaultWidth, defaultHeight,
	)

	fun openEditor(option: ManagedOption<HudMeta>, oldScreen: Screen) {
		Client.screen = HudEditors.editor(oldScreen, listOf(option.value))
	}

	override fun appendRows(opt: ManagedOption<HudMeta>, guiAppender: GuiAppender) {
		val caption = TextComponent(Component.translatable("apex.hud.edit").string)
		guiAppender.appendLabeledRow(opt.labelText, ApexButton(caption) {
			Client.screen?.let { current -> openEditor(opt, current) }
		})
	}
}
