package dev.ashtonite365.apex.features.items.recipes

import dev.ashtonite365.apex.util.bazaarStockOf
import io.github.moulberry.repo.data.NEUCraftingRecipe
import io.github.moulberry.repo.data.NEUForgeRecipe
import io.github.moulberry.repo.data.NEUKatUpgradeRecipe
import io.github.moulberry.repo.data.NEUMobDropRecipe
import io.github.moulberry.repo.data.NEUNpcShopRecipe
import io.github.moulberry.repo.data.NEURecipe
import io.github.moulberry.repo.data.NEUTradeRecipe
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.util.ItemId

enum class SourceKind(val label: String) {
	CRAFTING("Crafting"), FORGE("Forge"), NPC_SHOP("NPC Shop"), TRADE("Trade"), KAT_UPGRADE("Kat Upgrade"), OTHER("Other recipes");

	companion object {
		fun of(recipe: NEURecipe): SourceKind? = when (recipe) {
			is NEUMobDropRecipe -> null
			is NEUCraftingRecipe -> CRAFTING
			is NEUForgeRecipe -> FORGE
			is NEUNpcShopRecipe -> NPC_SHOP
			is NEUTradeRecipe -> TRADE
			is NEUKatUpgradeRecipe -> KAT_UPGRADE
			else -> OTHER
		}
	}
}

data class RecipeGroup(val kind: SourceKind, val count: Int)

data class PriceSnapshot(
	val bazaarBuy: Double? = null,
	val bazaarSell: Double? = null,
	val lowestBin: Double? = null,
	val avg1dLowestBin: Double? = null,
) {
	val hasAny: Boolean get() = listOf(bazaarBuy, bazaarSell, lowestBin, avg1dLowestBin).any { it != null }

	fun lines(): List<String> {
		if (!hasAny) return listOf("No live price")
		fun f(v: Double) = "%,.1f coins".format(v)
		return buildList {
			bazaarBuy?.let { add("Bazaar buy: ${f(it)}") }
			bazaarSell?.let { add("Bazaar sell: ${f(it)}") }
			lowestBin?.let { add("AH lowest BIN: ${f(it)}") }
			avg1dLowestBin?.let { add("AH 1-day avg BIN: ${f(it)}") }
		}
	}
}

data class HowToGetModel(val itemId: String, val recipeGroups: List<RecipeGroup>, val mobIds: List<String>) {
	val isEmpty: Boolean get() = recipeGroups.isEmpty() && mobIds.isEmpty()
}

object HowToGet {

	fun build(itemId: String, producing: Collection<NEURecipe>, mobIds: List<String>): HowToGetModel {
		val groups = producing
			.filter { r -> r.allOutputs.any { it.itemId == itemId } }
			.mapNotNull { SourceKind.of(it) }
			.groupingBy { it }.eachCount()
			.toSortedMap()
			.map { (k, n) -> RecipeGroup(k, n) }
		return HowToGetModel(itemId, groups, mobIds.distinct())
	}

	fun forItem(itemId: String): HowToGetModel =
		build(itemId, RepoManager.getRecipesFor(ItemId(itemId)), MobIndex.mobsDropping(itemId).map { it.id })

	fun livePrice(
		itemId: String,
		stockOf: (ItemId) -> ItemId.BazaarProduct = { bazaarStockOf(it) },
		bazaar: Map<ItemId.BazaarProduct, HypixelStaticData.BazaarData> = HypixelStaticData.bazaarData,
		lowestBin: Map<ItemId, Double> = HypixelStaticData.lowestBin,
		avg1d: Map<ItemId, Double> = HypixelStaticData.avg1dlowestBin,
	): PriceSnapshot {
		val id = ItemId(itemId)
		val bz = runCatching { bazaar[stockOf(id)] }.getOrNull()
		return PriceSnapshot(
			bz?.quickStatus?.buyPrice?.takeIf { it > 0 },
			bz?.quickStatus?.sellPrice?.takeIf { it > 0 },
			lowestBin[id],
			avg1d[id],
		)
	}
}
