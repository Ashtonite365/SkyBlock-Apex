@file:UseSerializers(DashlessUUIDSerializer::class)

package dev.ashtonite365.apex.features.inventory

import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import dev.ashtonite365.apex.util.json.DashlessUUIDSerializer

@Serializable
data class SlotBinding(val hotbar: Int, val inventory: Int)

@Serializable(with = SlotBindings.Codec::class)
data class SlotBindings(val pairs: MutableSet<SlotBinding> = mutableSetOf()) {

	fun involving(slot: Int): List<SlotBinding> = pairs.filter { slot == it.hotbar || slot == it.inventory }

	fun releaseInventorySlot(inventorySlot: Int) {
		pairs.removeAll { it.inventory == inventorySlot }
	}

	fun unbind(slot: Int): Boolean = pairs.removeAll { slot == it.hotbar || slot == it.inventory }

	fun bind(hotbar: Int, inventory: Int, exclusive: Boolean = !SlotLocking.TConfig.allowMultiBinding) {
		if (exclusive) {
			unbind(hotbar)
			unbind(inventory)
		}
		pairs += SlotBinding(hotbar, inventory)
	}

	object Codec : KSerializer<SlotBindings> {
		private val listForm = SetSerializer(SlotBinding.serializer())
		override val descriptor get() = listForm.descriptor

		override fun serialize(encoder: Encoder, value: SlotBindings) = listForm.serialize(encoder, value.pairs)

		override fun deserialize(decoder: Decoder): SlotBindings {
			val json = decoder as JsonDecoder
			return when (val tree = json.decodeJsonElement()) {
				is JsonObject -> SlotBindings(tree.mapTo(LinkedHashSet()) { (hotbar, inv) -> SlotBinding(hotbar.toInt(), inv.jsonPrimitive.int) })
				else -> SlotBindings(json.json.decodeFromJsonElement(listForm, tree).toMutableSet())
			}
		}
	}
}

@Serializable
data class LegacyIslandLocks(val lockedSlots: MutableSet<Int> = mutableSetOf(), val boundSlots: SlotBindings = SlotBindings())

@Serializable
data class LegacyProfileLocks(
	val lockedUUIDs: MutableSet<UUID> = mutableSetOf(),
	val rift: LegacyIslandLocks = LegacyIslandLocks(),
	val overworld: LegacyIslandLocks = LegacyIslandLocks(),
)
