// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.util.skyblock

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import dev.ashtonite365.apex.util.StringUtil.words
import dev.ashtonite365.apex.util.bareText
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.petBlob
import io.github.moulberry.repo.data.Rarity as RepoRarity

@Serializable(with = Rarity.ByName::class)
enum class Rarity {
	COMMON,
	UNCOMMON,
	RARE,
	EPIC,
	LEGENDARY,
	MYTHIC,
	DIVINE,
	SUPREME,
	SPECIAL,
	VERY_SPECIAL,
	ULTIMATE,
	ADMIN,
	UNKNOWN,
	;

	val names: Set<String> = if (name == "LEGENDARY") setOf(name, "LEGENJERRY") else setOf(name)

	val text: Component
		get() {
			val colour = colourMap[this] ?: return Component.literal(name)
			return Component.literal(name).withStyle(Style.EMPTY.withColor(colour))
		}

	val coreRepoRarity: RepoRarity? = RepoRarity.entries.firstOrNull { it.name == name }

	object ByName : KSerializer<Rarity> {
		override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Rarity", PrimitiveKind.STRING)

		override fun deserialize(decoder: Decoder): Rarity = valueOf(decoder.decodeString().replace(' ', '_'))

		override fun serialize(encoder: Encoder, value: Rarity) = encoder.encodeString(value.name)
	}

	companion object Lookup {
		val colourMap: Map<Rarity, ChatFormatting> = mapOf(
			COMMON to ChatFormatting.WHITE,
			UNCOMMON to ChatFormatting.GREEN,
			RARE to ChatFormatting.BLUE,
			EPIC to ChatFormatting.DARK_PURPLE,
			LEGENDARY to ChatFormatting.GOLD,
			MYTHIC to ChatFormatting.LIGHT_PURPLE,
			DIVINE to ChatFormatting.AQUA,
			SUPREME to ChatFormatting.DARK_RED,
			SPECIAL to ChatFormatting.RED,
			VERY_SPECIAL to ChatFormatting.RED,
			ULTIMATE to ChatFormatting.DARK_RED,
			ADMIN to ChatFormatting.DARK_RED,
		)

		val byName: Map<String, Rarity> = buildMap {
			for (rarity in Rarity.entries) for (alias in rarity.names) put(alias, rarity)
		}

		val fromCoreRepo: Map<RepoRarity?, Rarity> = entries.associateBy { it.coreRepoRarity }

		fun fromCoreRepo(rarity: RepoRarity): Rarity? = fromCoreRepo[rarity]

		fun fromString(name: String): Rarity? = byName[name]

		fun fromTier(tier: Int): Rarity? = entries.getOrNull(tier)

		fun fromItem(item: ItemView): Rarity? = fromLore(item.loreAccordingToNbt) ?: fromPetItem(item)

		fun fromPetItem(item: ItemView): Rarity? = item.petBlob?.rarity?.let(::fromCoreRepo)

		fun fromLore(lore: List<Component>): Rarity? {
			for (line in lore.asReversed()) {
				val found = line.bareText.words().firstNotNullOfOrNull { byName[it] }
				if (found != null) return found
			}
			return null
		}
	}
}
