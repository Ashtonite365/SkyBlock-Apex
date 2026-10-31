package dev.ashtonite365.apex.features.profileviewer

import io.github.moulberry.repo.data.Rarity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.optionals.getOrNull
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NumericTag
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ReforgeStore
import dev.ashtonite365.apex.util.ReforgeKey
import dev.ashtonite365.apex.util.ItemId

object Networth {
	data class Breakdown(
		val purse: Double,
		val bank: Double,
		val items: Double,
		val enchantments: Double,
		val reforges: Double,
		val recombobulators: Double,
		val gemstones: Double,
		val books: Double,
		val pets: Double,
		val sacks: Double,
		val essence: Double,
	) {
		val total get() = purse + bank + items + enchantments + reforges + recombobulators + gemstones + books + pets + sacks + essence
	}

	const val APPLIED_WORTH = 0.85

	private fun price(id: String) = HypixelStaticData.getPriceOfItem(ItemId(id)) ?: 0.0

	fun calculate(profile: ProfileViewer.SkyblockProfile): Breakdown {
		val member = profile.member
		var items = 0.0
		var enchants = 0.0
		var reforges = 0.0
		var recombs = 0.0
		var gems = 0.0
		var books = 0.0
		for (item in member.allProfileItems()) {
			val id = item.catalogItemId ?: continue
			items += price(id.skyblockId) * item.count
			val extra = item.tag?.getCompound("ExtraAttributes")?.getOrNull() ?: continue
			extra.getCompound("enchantments").getOrNull()?.let { ench ->
				for (name in ench.keySet()) {
					val level = (ench.get(name) as? NumericTag)?.intValue() ?: continue
					enchants += price("${name.uppercase()};$level") * APPLIED_WORTH
				}
			}
			extra.getString("modifier").getOrNull()?.let { modifier ->
				val stone = ReforgeStore.modifierLut[ReforgeKey(modifier)]?.stoneId
				if (stone != null) reforges += price(stone.skyblockId) * APPLIED_WORTH
			}
			if (((extra.get("rarity_upgrades") as? NumericTag)?.intValue() ?: 0) > 0)
				recombs += price("RECOMBOBULATOR_3000") * APPLIED_WORTH
			extra.getCompound("gems").getOrNull()?.let { gems += gemstoneValue(it) * APPLIED_WORTH }
			val hpb = (extra.get("hot_potato_count") as? NumericTag)?.intValue() ?: 0
			books += (minOf(hpb, 10) * price("HOT_POTATO_BOOK") + maxOf(hpb - 10, 0) * price("FUMING_POTATO_BOOK")) * APPLIED_WORTH
			if (((extra.get("art_of_war_count") as? NumericTag)?.intValue() ?: 0) > 0) books += price("THE_ART_OF_WAR") * APPLIED_WORTH
		}
		var pets = 0.0
		(member.path("pets_data", "pets") as? JsonArray)?.forEach { pet ->
			pet as? JsonObject ?: return@forEach
			val rarity = runCatching { Rarity.valueOf(pet["tier"].str() ?: "") }.getOrNull() ?: return@forEach
			pets += price("${pet["type"].str()};${rarity.ordinal}")
			pet["heldItem"].str()?.let { pets += price(it) * APPLIED_WORTH }
		}
		var sacks = 0.0
		(member.path("inventory", "sacks_counts") as? JsonObject)?.forEach { (id, count) ->
			sacks += price(id) * (count.long() ?: 0)
		}
		var essence = 0.0
		(member.path("currencies", "essence") as? JsonObject)?.forEach { (type, data) ->
			essence += price("ESSENCE_$type") * ((data as? JsonObject)?.get("current").long() ?: 0)
		}
		return Breakdown(
			purse = member.path("currencies", "coin_purse").num() ?: 0.0,
			bank = profile.bank ?: 0.0,
			items = items, enchantments = enchants, reforges = reforges, recombobulators = recombs,
			gemstones = gems, books = books, pets = pets, sacks = sacks, essence = essence,
		)
	}

	private fun gemstoneValue(gems: CompoundTag): Double {
		var total = 0.0
		for (slot in gems.keySet()) {
			if (slot.endsWith("_gem") || slot == "unlocked_slots") continue
			val quality = gems.getString(slot).getOrNull()
				?: gems.getCompound(slot).getOrNull()?.getString("quality")?.getOrNull()
				?: continue
			val type = gems.getString("${slot}_gem").getOrNull() ?: slot.substringBefore('_')
			total += price("${quality}_${type}_GEM")
		}
		return total
	}
}
