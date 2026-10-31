package dev.ashtonite365.apex.gui.config

import io.github.notenoughupdates.moulconfig.ChromaColour
import io.github.notenoughupdates.moulconfig.gui.component.ColorSelectComponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import dev.ashtonite365.apex.util.data.ManagedConfig

class ColourHandler(val config: ManagedConfig) : ManagedConfig.OptionCodec<ChromaColour> {

	@Serializable
	@SerialName("dev.ashtonite365.apex.gui.config.ColourHandler.ChromaDelegate")
	private class SavedChroma(
		@SerialName("h") val hue: Float,
		@SerialName("s") val saturation: Float,
		@SerialName("b") val brightness: Float,
		@SerialName("a") val alpha: Int,
		@SerialName("c") val chromaMillis: Int,
	)

	object ChromaSerializer :
		KSerializer<ChromaColour> {
		private val surrogate = SavedChroma.serializer()
		override val descriptor: SerialDescriptor = SerialDescriptor("ApexChromaColour", surrogate.descriptor)

		override fun serialize(encoder: Encoder, value: ChromaColour) {
			val saved = with(value) { SavedChroma(hue, saturation, brightness, alpha, timeForFullRotationInMillis) }
			encoder.encodeSerializableValue(surrogate, saved)
		}

		override fun deserialize(decoder: Decoder): ChromaColour =
			decoder.decodeSerializableValue(surrogate).run {
				ChromaColour(hue, saturation, brightness, chromaMillis, alpha)
			}
	}

	override fun encode(element: ChromaColour): JsonElement? = Json.encodeToJsonElement(ChromaSerializer, element)
	override fun decode(element: JsonElement): ChromaColour = Json.decodeFromJsonElement(ChromaSerializer, element)

	override fun appendRows(opt: ManagedOption<ChromaColour>, guiAppender: GuiAppender) {
		val picker = ColorSelectComponent(
			0, 0,
			opt.value.toLegacyString(),
			{ legacy ->
				opt.value = ChromaColour.forLegacyString(legacy)
				config.markDirty()
			},
			{},
		)
		guiAppender.appendLabeledRow(opt.labelText, picker)
	}
}
