package dev.ashtonite365.apex.gui.config

import com.mojang.serialization.Codec
import io.github.notenoughupdates.moulconfig.gui.component.RowComponent
import io.github.notenoughupdates.moulconfig.gui.component.TextComponent
import kotlinx.serialization.json.JsonElement
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.gui.CheckboxComponent
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.json.KJsonOps

class ChoiceHandler<C>(val enumClass: Class<C>, val universe: List<C>) : ManagedConfig.OptionCodec<C>
	where C : StringRepresentable, C : Enum<C> {

	val renderer: EnumRenderer<C> = EnumRenderer.default()

	@Suppress("UNCHECKED_CAST")
	val codec: Codec<C> = StringRepresentable.fromValues {
		val slots = java.lang.reflect.Array.newInstance(enumClass, universe.size) as Array<C>
		universe.forEachIndexed { at, choice -> slots[at] = choice }
		slots
	}

	override fun encode(element: C): JsonElement? {
		val outcome = codec.encodeStart(KJsonOps.INSTANCE, element)
		outcome.error().ifPresent { Faults.reportFault("Failed to encode json element '$element': ${it.message()}") }
		return outcome.result().orElse(null)
	}

	override fun decode(element: JsonElement): C = codec.parse(KJsonOps.INSTANCE, element)
		.getOrThrow { problem -> IllegalArgumentException("Failed to decode json element '$element': $problem") }

	override fun isRetired(element: JsonElement): Boolean = RetiredChoices.isRetired(enumClass.name, element)

	override fun appendRows(opt: ManagedOption<C>, guiAppender: GuiAppender) {
		val state = opt.alsoMarking(opt.element)
		guiAppender.appendFullRow(TextComponent(opt.labelText.string))
		for (choice in universe) {
			val caption = TextComponent(renderer.getName(opt, choice).string)
			guiAppender.appendFullRow(RowComponent(CheckboxComponent(state, choice), caption))
		}
	}
}
