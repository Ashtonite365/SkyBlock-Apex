package dev.ashtonite365.apex.util

import com.mojang.serialization.Codec
import io.github.moulberry.repo.data.NEUIngredient
import io.github.moulberry.repo.data.NEUItem
import io.github.moulberry.repo.data.Rarity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.jvm.optionals.getOrNull
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import dev.ashtonite365.apex.repo.ExpLadders
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.collections.WeakKeyCache
import dev.ashtonite365.apex.util.json.DashlessUUIDSerializer
import dev.ashtonite365.apex.util.mc.NbtSnapshotGuard
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.ItemEditor
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.unsafeNbt
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.skyblock.ScreenIdentification
import dev.ashtonite365.apex.util.skyblock.ScreenType

@JvmInline
@Serializable
value class ItemId(val skyblockId: String) : Comparable<ItemId> {

	val identifier: Identifier
		get() = Identifier.fromNamespaceAndPath("skyblockitem", buildString {
			for (ch in skyblockId.lowercase()) when {
				ch == ';' -> append("__")
				ch == ':' -> append("___")
				ch in 'a'..'z' || ch in '0'..'9' || ch == '_' || ch == '.' || ch == '/' -> append(ch)
				else -> append("__").append(String.format(Locale.ROOT, "%04x", ch.code))
			}
		})

	override fun toString(): String = skyblockId

	override fun compareTo(other: ItemId): Int = skyblockId.compareTo(other.skyblockId)

	@JvmInline
	@Serializable
	value class BazaarProduct(val stockKey: String)

	companion object Known {
		val COINS: ItemId = ItemId("SKYBLOCK_COIN")
		val EMPTY_SLOT: ItemId = ItemId("NEU_SENTINEL_EMPTY")
		val NULL: ItemId = ItemId("null")
		val UNKNOWN_PET: ItemId = ItemId("null_pet")
		val CODEC: Codec<ItemId> = Codec.STRING.xmap(::ItemId, ItemId::skyblockId)
		val PACKET_CODEC: StreamCodec<in RegistryFriendlyByteBuf, ItemId> =
			ByteBufCodecs.STRING_UTF8.map(::ItemId, ItemId::skyblockId)
	}
}

val NEUItem.catalogItemId get() = ItemId(skyblockItemId)
val NEUIngredient.catalogItemId get() = ItemId(itemId)
val ItemId.bazaarProduct get() = bazaarStockOf(this)

fun bazaarStockOf(item: ItemId): ItemId.BazaarProduct =
	ItemId.BazaarProduct(RepoManager.coreRepo.constants.bazaarStocks?.getBazaarStockOrDefault(item.skyblockId) ?: item.skyblockId)

val ItemView.isEnchantedBook: Boolean get() = itemType.value() === Items.ENCHANTED_BOOK

@ExpensiveItemCacheApi
fun NEUItem.hypixelRecipeId(): String? {
	val variant = skyblockItemId.split(";")
	if (variant.size < 2) return skyblockItemId
	val made = asItemStack()
	return when {
		made.isEnchantedBook -> "ENCHANTED_BOOK_${variant[0]}_${variant[1]}"
		made.petBlob != null -> variant[0]
		else -> null
	}
}

@Serializable
data class PetInfoJson(
	@SerialName("type")
	val species: String,
	@SerialName("tier")
	val rarity: Rarity,
	@SerialName("exp")
	val xp: Double = 0.0,
	@SerialName("candyUsed")
	val candies: Int = 0,
	@SerialName("uuid")
	@Serializable(with = DashlessUUIDSerializer::class)
	val petUuid: UUID? = null,
	@SerialName("active")
	val equipped: Boolean? = false,
	@SerialName("heldItem")
	val heldItemId: String? = null,
) {

	val catalogItemId: ItemId get() = ItemId("${species.uppercase()};${rarity.ordinal}")

	val level get() = ExpLadders.getExpLadder(species.uppercase(), rarity).getPetLevel(xp)
}

private val ItemView.readOnlyCustomData: CompoundTag?
	get() = get(DataComponents.CUSTOM_DATA)?.let { NbtSnapshotGuard.watchedCopy(it.unsafeNbt) }

val ItemView.customDataTag: CompoundTag
	get() = get(DataComponents.CUSTOM_DATA)?.copyTag() ?: CompoundTag()

var ItemEditor.customDataTag: CompoundTag
	get() = (this as ItemView).customDataTag
	set(value) {
		set(DataComponents.CUSTOM_DATA, CustomData.of(value))
	}

fun ItemEditor.updateCustomData(edit: (CompoundTag) -> Unit) {
	val working = customDataTag
	edit(working)
	customDataTag = working
}

private fun ItemView.customString(key: String): String? = readOnlyCustomData?.getString(key)?.getOrNull()
private fun ItemView.customInt(key: String): Int? = readOnlyCustomData?.getInt(key)?.getOrNull()

val ItemView.itemUuidText: String? get() = customString("uuid")?.takeIf { it.isNotBlank() }

private val legacyTimestamp: DateTimeFormatter = DateTimeFormatterBuilder()
	.parseCaseInsensitive()
	.appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral('/')
	.appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral('/')
	.appendValueReduced(ChronoField.YEAR, 2, 2, 1970).appendLiteral(' ')
	.appendValue(ChronoField.CLOCK_HOUR_OF_AMPM, 1, 2, SignStyle.NOT_NEGATIVE).appendLiteral(':')
	.appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(' ')
	.appendText(ChronoField.AMPM_OF_DAY)
	.toFormatter(Locale.US)

private val hypixelZone: ZoneId = ZoneId.of("America/New_York")

val ItemView.timestamp: Instant?
	get() {
		val tag = readOnlyCustomData ?: return null
		tag.getLong("timestamp").getOrNull()?.let { return Instant.ofEpochMilli(it) }
		val written = tag.getString("timestamp").getOrNull() ?: return null
		return runCatching { LocalDateTime.parse(written, legacyTimestamp).atZone(hypixelZone).toInstant() }
			.getOrElse { error("Could not parse timestamp $written") }
	}

val ItemView.itemUuid: UUID? get() = itemUuidText?.let(UUID::fromString)

private val petJsonFormat = Json { ignoreUnknownKeys = true }

private val petInfoCache = WeakKeyCache.perKey<String, PetInfoJson>("PetInfo") { text ->
	try {
		petJsonFormat.decodeFromString(PetInfoJson.serializer(), text)
	} catch (e: Exception) {
		throw IllegalStateException("Could not decode hypixel pet info", e)
	}
}

val ItemView.petBlob: PetInfoJson?
	get() = customString("petInfo")?.takeIf { it.isNotBlank() }?.let(petInfoCache)

fun ItemView.starCount(): Int =
	listOf("upgrade_level", "dungeon_item_level").firstNotNullOfOrNull { key -> customInt(key)?.takeIf { it > 0 } } ?: 0

@Serializable
@JvmInline
value class ReforgeKey(val id: String)

fun ItemView.reforgeKey(): ReforgeKey? = customString("modifier")?.takeIf { it.isNotBlank() }?.let(::ReforgeKey)

fun <T : ItemEditor> T.withApexUiId(uiId: String) = withItemId(ItemId("APEX_UI_$uiId"))

fun <T : ItemEditor> T.withItemId(catalogItemId: ItemId): T {
	updateCustomData { it.putString("id", catalogItemId.skyblockId) }
	return this
}

private val amountStatements: List<Pattern> = listOf(
	" Amount: ($SHORT_NUMBER_FORMAT)",
	"Stored: ($SHORT_NUMBER_FORMAT)/.+",
	".*(?:Offer amount|Amount|Order amount): ($SHORT_NUMBER_FORMAT)x",
	"Compost Available: ($SHORT_NUMBER_FORMAT)",
).map(Pattern::compile)

fun ItemView.statedAmount(): Long {
	for (line in loreAccordingToNbt) {
		val text = line.string
		for (statement in amountStatements) {
			val m = statement.matcher(text)
			if (m.matches()) return parseShortNumber(m.group(1)).toLong()
		}
	}
	return amount.toLong()
}

val ItemView.hypixelIdText: String? get() = customString("id")

fun ItemView.guessIdFromMenu(): ItemId? {
	val kind = ScreenIdentification.getType(Client.screen) ?: return null
	val shown = displayNameAccordingToNbt.bareText
	val lines = loreAccordingToNbt
	fun firstLoreText() = lines.firstOrNull()?.bareText
	return when (kind) {
		ScreenType.BAZAAR_ANY -> {
			if (isEnchantedBook) firstLoreText()?.let { ItemNameLookup.guessItemByName(it, false) }
			else ItemNameLookup.guessItemByName(shown.replaceFirst("SELL ", "").replaceFirst("BUY ", ""), false)
		}
		ScreenType.DYE_COMPENDIUM, ScreenType.ENCHANTMENT_GUIDE -> ItemNameLookup.guessItemByName(shown, false)
		ScreenType.SUPER_PAIRS -> {
			val read = if (isEnchantedBook) firstLoreText() else shown
			read?.let { ItemNameLookup.guessItemByName(it, true) }
		}
		ScreenType.EXPERIMENTATION_RNG_METER -> {

			val gap = lines.indexOfFirst { it.bareText.isBlank() }
			val reward = if (gap < 0) null else lines.drop(gap + 1).firstOrNull { it.bareText.isNotBlank() }
			reward?.let { ItemNameLookup.guessItemByName(it.bareText, true) }
		}
	}
}

@NeedsComponents
val ItemView.labelName: Component
	get() = get(DataComponents.CUSTOM_NAME)
		?: get(DataComponents.ITEM_NAME)
		?: itemType.components().get(DataComponents.ITEM_NAME)
		?: CommonComponents.EMPTY

private fun CompoundTag.soleLevelled(field: String, shape: (String) -> String = { it }): String? {
	val inner = getCompound(field).getOrNull() ?: return null
	val keys = inner.keySet()
	if (keys.size != 1) return null
	val key = keys.single()
	return "${shape(key.uppercase())};${inner.getInt(key).getOrNull()}"
}

private fun spaced(text: String) = text.uppercase().replace(" ", "_")

val ItemView.repoItemId: ItemId?
	get() {
		val raw = hypixelIdText?.takeIf { it.isNotEmpty() } ?: return guessIdFromMenu()
		val tag = readOnlyCustomData ?: CompoundTag()
		val refined = when (raw) {
			"PET" -> return petBlob?.catalogItemId ?: ItemId.UNKNOWN_PET
			"RUNE", "UNIQUE_RUNE" -> tag.soleLevelled("runes") { "${it}_RUNE" } ?: "RUNE"
			"ENCHANTED_BOOK" -> tag.soleLevelled("enchantments") ?: "ENCHANTED_BOOK"
			"ATTRIBUTE_SHARD" -> tag.soleLevelled("attributes") { "ATTRIBUTE_SHARD_$it" } ?: "ATTRIBUTE_SHARD"
			"ABICASE" -> "ABICASE_${customString("model")?.uppercase()}"
			"POTION" -> {
				val strength = customInt("potion_level")
				customString("potion_name")?.let { "POTION_${spaced(it)};$strength" }
					?: customString("potion")?.let { "POTION_${spaced(it)};$strength" }
					?: customString("potion_type")?.let { "POTION_${spaced(it)}" }
					?: "WATER_BOTTLE"
			}
			"PARTY_HAT_SLOTH" -> "PARTY_HAT_SLOTH_${customString("party_hat_emoji")?.uppercase()}"
			"PARTY_HAT_CRAB", "PARTY_HAT_CRAB_ANIMATED", "CAKE_HAT_2026" -> {
				val tint = customString("party_hat_color")?.uppercase()
				when {
					customInt("party_hat_year") == 2022 -> "PARTY_HAT_CRAB_${tint}_ANIMATED"
					raw == "CAKE_HAT_2026" -> "CAKE_HAT_2026_$tint"
					else -> "PARTY_HAT_CRAB_$tint"
				}
			}
			"BALLOON_HAT_2024", "BALLOON_HAT_2025" ->
				"BALLOON_HAT_${customInt("party_hat_year")}_${customString("party_hat_color")?.uppercase()}"
			else -> raw
		}
		return ItemId(refined.replace(":", "-"))
	}
