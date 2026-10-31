// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.ReforgeKey
import dev.ashtonite365.apex.util.skyblock.ItemType
import dev.ashtonite365.apex.util.skyblock.Rarity

@Serializable
data class Reforge(
	@SerialName("reforgeName") val prefixName: String,

	@SerialName("internalName") val stoneId: ItemId? = null,
	@SerialName("nbtModifier") val modifierOverride: ReforgeKey? = null,
	@SerialName("requiredRarities") val onlyRarities: List<Rarity>? = null,
	@SerialName("itemTypes") val typeField: @Serializable(Reforge.Eligibility.TypesFieldSerializer::class) List<Reforge.Eligibility>? = null,
	@SerialName("allowOn") val allowList: List<Reforge.Eligibility>? = null,
	@SerialName("reforgeCosts") val costByTier: Reforge.PerTier<Double>? = null,
	@SerialName("reforgeAbility") val abilityByTier: Reforge.PerTier<String>? = null,
	@SerialName("reforgeStats") val statsByTier: Reforge.PerTier<Map<String, Double>>? = null,
) {

	val reforgeId: ReforgeKey get() = modifierOverride ?: ReforgeKey(prefixName.lowercase())

	val eligibleItems: List<Eligibility> get() = allowList ?: typeField ?: emptyList()

	val statUniverse: Set<String> = buildSet {
		val stats = statsByTier ?: return@buildSet
		for (rarity in Rarity.entries) stats.get(rarity)?.let { addAll(it.keys) }
	}

	@Serializable(with = Eligibility.Serializer::class)
	sealed interface Eligibility {

		data class OfKind(val kind: ItemType) : Eligibility

		data class OfSkyblockId(val id: ItemId) : Eligibility

		data class OfVanillaItem(val key: ResourceKey<Item>) : Eligibility

		object TypesFieldSerializer : KSerializer<List<Eligibility>> {
			override val descriptor: SerialDescriptor get() = JsonElement.serializer().descriptor

			override fun serialize(encoder: Encoder, value: List<Eligibility>) = notWritable()

			override fun deserialize(decoder: Decoder): List<Eligibility> {
				val json = decoder as JsonDecoder
				return when (val element = json.decodeJsonElement()) {
					is JsonPrimitive -> element.content.split('/').map { OfKind(ItemType.ofName(it)) }
					is JsonArray -> element.map { json.json.decodeFromJsonElement(Serializer, it) }
					is JsonObject -> {
						val result = ArrayList<Eligibility>()
						element["internalName"]?.jsonArray?.forEach { result += OfSkyblockId(ItemId(it.jsonPrimitive.content)) }
						element["itemId"]?.jsonArray?.forEach { id ->
							vanillaKey(id.jsonPrimitive.content)?.let { result += OfVanillaItem(it) }
						}
						result
					}
				}
			}
		}

		object Serializer : KSerializer<Eligibility> {
			override val descriptor: SerialDescriptor get() = JsonElement.serializer().descriptor

			override fun serialize(encoder: Encoder, value: Eligibility) = notWritable()

			override fun deserialize(decoder: Decoder): Eligibility {
				val rule = (decoder as JsonDecoder).decodeJsonElement() as JsonObject
				fun text(key: String) = rule[key]?.jsonPrimitive?.content
				text("internalName")?.let { return OfSkyblockId(ItemId(it)) }
				text("itemType")?.let { return OfKind(ItemType.ofName(it)) }
				text("minecraftId")?.let { id -> return OfVanillaItem(vanillaKey(id) ?: error("Bad minecraftId $id")) }
				error("Reforge filter has none of internalName, itemType, minecraftId: $rule")
			}
		}
	}

	@Serializable(with = PerTier.Serializer::class)
	sealed interface PerTier<T> {
		fun get(rarity: Rarity?): T?

		@Serializable
		data class Uniform<T>(val value: T) : PerTier<T> {
			override fun get(rarity: Rarity?): T = value
		}

		@Serializable
		data class Tabled<T>(val values: Map<Rarity, T>) : PerTier<T> {
			override fun get(rarity: Rarity?): T? = rarity?.let { values[it] }
		}

		class Serializer<T>(private val single: KSerializer<T>) : KSerializer<PerTier<T>> {
			private val table: KSerializer<Map<Rarity, T>> = MapSerializer(Rarity.serializer(), single)

			override val descriptor: SerialDescriptor get() = JsonElement.serializer().descriptor

			override fun serialize(encoder: Encoder, value: PerTier<T>) = notWritable()

			override fun deserialize(decoder: Decoder): PerTier<T> {
				val json = decoder as JsonDecoder
				val element = json.decodeJsonElement()
				return if (element is JsonObject) Tabled(json.json.decodeFromJsonElement(table, element))
				else Uniform(json.json.decodeFromJsonElement(single, element))
			}
		}
	}
}

private fun notWritable(): Nothing = throw NotImplementedError("An operation is not implemented: Not yet implemented")

private fun vanillaKey(id: String): ResourceKey<Item>? =
	Identifier.tryParse(id)?.let { ResourceKey.create(Registries.ITEM, it) }
