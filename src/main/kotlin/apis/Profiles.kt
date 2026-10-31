@file:UseSerializers(InstantAsLongSerializer::class, DashlessUUIDSerializer::class)

package dev.ashtonite365.apex.apis

import io.github.moulberry.repo.constants.Leveling
import io.github.moulberry.repo.data.Rarity
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.reflect.KProperty1
import net.minecraft.world.item.DyeColor
import net.minecraft.ChatFormatting
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.util.LegacyFormattingCode
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.nonNullOr
import dev.ashtonite365.apex.util.json.DashlessUUIDSerializer
import dev.ashtonite365.apex.util.json.InstantAsLongSerializer

@Serializable data class CollectionResponse(val success: Boolean, val collections: Map<String, CollectionSkillData>)

@Serializable data class CollectionSkillData(val items: Map<CollectionType, CollectionInfo>)

@Serializable data class CollectionInfo(val name: String, val maxTiers: Int, val tiers: List<CollectionTier>)

@Serializable data class CollectionTier(val tier: Int, val amountRequired: Long, val unlocks: List<String>)

@Serializable @JvmInline value class CollectionType(val string: String) {
	val catalogItemId get() = ItemId(collectionItemAliases.fold(string) { key, (from, to) -> key.replace(from, to) })
}

private val collectionItemAliases = listOf(":" to "-", "MUSHROOM_COLLECTION" to "HUGE_MUSHROOM_2")

@Serializable data class Profiles(val success: Boolean, val profiles: List<Profile>?)

@Serializable data class Profile(
	@SerialName("profile_id") val profileId: UUID, @SerialName("cute_name") val cuteName: String,
	@SerialName("selected") val isSelected: Boolean = false,
	@SerialName("members") val players: Map<UUID, Member>,
)

@Serializable data class Member(
	val pets: List<Pet> = emptyList(),
	@SerialName("coop_invitation") val coopInvitation: CoopInvitation? = null,
	@SerialName("experience_skill_farming") val experienceSkillFarming: Double = 0.0,
	@SerialName("experience_skill_alchemy") val experienceSkillAlchemy: Double = 0.0,
	@SerialName("experience_skill_combat") val experienceSkillCombat: Double = 0.0,
	@SerialName("experience_skill_taming") val experienceSkillTaming: Double = 0.0,
	@SerialName("experience_skill_social2") val experienceSkillSocial: Double = 0.0,
	@SerialName("experience_skill_enchanting") val experienceSkillEnchanting: Double = 0.0,
	@SerialName("experience_skill_fishing") val experienceSkillFishing: Double = 0.0,
	@SerialName("experience_skill_foraging") val experienceSkillForaging: Double = 0.0,
	@SerialName("experience_skill_mining") val experienceSkillMining: Double = 0.0,
	@SerialName("experience_skill_runecrafting") val experienceSkillRunecrafting: Double = 0.0,
	@SerialName("experience_skill_carpentry") val experienceSkillCarpentry: Double = 0.0,
	val collection: Map<CollectionType, Long> = emptyMap(),
)

@Serializable data class CoopInvitation(val timestamp: Instant, @SerialName("invited_by") val invitedBy: UUID? = null, val confirmed: Boolean)

@Serializable @JvmInline value class PetType(val name: String)

@Serializable data class Pet(
	@SerialName("uuid") val petUuid: UUID? = null,
	val type: PetType,
	@SerialName("exp") val xp: Double = 0.0,
	@SerialName("active") val equipped: Boolean = false,
	val tier: Rarity,
	@SerialName("candyUsed") val candies: Int = 0,
	@SerialName("heldItem") val heldItemId: String? = null,
	@SerialName("skin") val skinId: String? = null,
) {

	val itemId: ItemId get() = ItemId(type.name + ";" + tier.ordinal)
}

enum class Skill(val color: DyeColor, iconId: String, val xpOf: KProperty1<Member, Double>) {
	FARMING(DyeColor.YELLOW, "ROOKIE_HOE", Member::experienceSkillFarming),
	FORAGING(DyeColor.BROWN, "TREECAPITATOR_AXE", Member::experienceSkillForaging),
	MINING(DyeColor.LIGHT_GRAY, "DIAMOND_PICKAXE", Member::experienceSkillMining),
	ALCHEMY(DyeColor.PURPLE, "BREWING_STAND", Member::experienceSkillAlchemy),
	TAMING(DyeColor.GREEN, "SUPER_EGG", Member::experienceSkillTaming),
	FISHING(DyeColor.BLUE, "FARMER_ROD", Member::experienceSkillFishing),
	RUNECRAFTING(DyeColor.PINK, "MUSIC_RUNE;1", Member::experienceSkillRunecrafting),
	CARPENTRY(DyeColor.ORANGE, "WORKBENCH", Member::experienceSkillCarpentry),
	COMBAT(DyeColor.RED, "UNDEAD_SWORD", Member::experienceSkillCombat),
	SOCIAL(DyeColor.WHITE, "EGG_HUNT", Member::experienceSkillSocial),
	ENCHANTING(DyeColor.MAGENTA, "ENCHANTMENT_TABLE", Member::experienceSkillEnchanting),
	;

	val icon: ItemId = ItemId(iconId)

	private val repoKey: String get() = name.lowercase()

	fun levelCap(table: Leveling): Int = nonNullOr(table.maximumLevels[repoKey]) { 50 }

	fun xpLadder(table: Leveling): List<Int> = when (this) {
		SOCIAL -> table.socialExperienceRequiredPerLevel
		RUNECRAFTING -> table.runecraftingExperienceRequiredPerLevel
		else -> table.skillExperienceRequiredPerLevel
	}
}

enum class CollectionCategory(

	val skill: Skill?, ownColor: DyeColor? = null, ownIcon: ItemId? = null,
) {
	FARMING(Skill.FARMING), FORAGING(Skill.FORAGING), MINING(Skill.MINING), FISHING(Skill.FISHING), COMBAT(Skill.COMBAT),
	RIFT(null, DyeColor.PURPLE, ItemId("SKYBLOCK_MOTE"));

	val color: DyeColor = ownColor ?: skill!!.color
	val icon: ItemId = ownIcon ?: skill!!.icon
}

@Serializable data class PlayerResponse(val success: Boolean, val player: PlayerData)

@Serializable data class PlayerData(
	@SerialName("uuid") val playerUuid: UUID,
	@SerialName("firstLogin") val joinedAt: Instant,
	@SerialName("lastLogin") val lastSeen: Instant? = null,
	@SerialName("playername") val playerName: String,
	@SerialName("achievementsOneTime") val oneTimeAchievements: List<String> = emptyList(),
	@SerialName("newPackageRank") val packageRank: String? = null, val monthlyPackageRank: String? = null,
	val rankPlusColor: String = "GOLD",
) {

	val rankPlusDyeColor: LegacyFormattingCode = LegacyFormattingCode.entries.firstOrNull { it.name == rankPlusColor } ?: LegacyFormattingCode.GOLD

	private val effectiveRank: String? get() = if (monthlyPackageRank != null && monthlyPackageRank != "NONE") monthlyPackageRank else packageRank

	val rankData get() = effectiveRank?.let { SafeRepoConstants.ranks()[it] }

	fun getDisplayName(name: String = playerName): String {
		val rank = rankData ?: return LegacyFormattingCode.GRAY.formattingCode + name
		val plusCode = if (rank.plus == null) "" else rankPlusDyeColor.formattingCode
		return "§${rank.color}[${rank.tag}$plusCode${rank.plus ?: ""}§${rank.color}] $name"
	}
}

@Serializable data class MowojangNameLookup(val name: String, val id: UUID)
