// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepoFile
import io.github.moulberry.repo.NEURepository
import io.github.moulberry.repo.NEURepositoryException
import io.github.moulberry.repo.data.NEURecipe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.minecraft.world.item.Item
import net.minecraft.resources.ResourceKey
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.ReforgeKey
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.json.KJsonOps
import dev.ashtonite365.apex.util.skyblock.ItemType

object ReforgeStore
	: ExtraRecipeProvider, IReloadable {
	var allReforges: List<Reforge> = emptyList()
	var modifierLut: Map<ReforgeKey, Reforge> = emptyMap()
	var byReforgeStone: Map<ItemId, Reforge> = emptyMap()
	var byType: Map<ItemType, List<Reforge>> = emptyMap()
	var byInternalName: Map<ItemId, List<Reforge>> = emptyMap()
	var byVanilla: Map<ResourceKey<Item>, List<Reforge>> = emptyMap()

	private val umbrellas: List<Pair<ItemType, List<ItemType>>> by lazy {
		listOf(
			ItemType.SWORD to listOf(ItemType.SWORD, ItemType.GAUNTLET, ItemType.LONGSWORD, ItemType.FISHING_WEAPON),
			ItemType.ofName("ARMOR") to listOf(ItemType.CHESTPLATE, ItemType.LEGGINGS, ItemType.HELMET, ItemType.BOOTS),
			ItemType.EQUIPMENT to listOf(ItemType.CLOAK, ItemType.BRACELET, ItemType.NECKLACE, ItemType.BELT, ItemType.GLOVES),
			ItemType.ROD to listOf(ItemType.FISHING_ROD, ItemType.FISHING_WEAPON),
		)
	}

	fun resolveItemType(itemType: ItemType): List<ItemType> =
		umbrellas.firstOrNull { it.first == itemType }?.second ?: listOf(itemType)

	fun findEligibleForItem(itemType: ItemType): List<Reforge> = byType[itemType].orEmpty()

	fun findEligibleForInternalName(internalName: ItemId): List<Reforge> = byInternalName[internalName].orEmpty()

	override fun provideExtraRecipes(): Iterable<NEURecipe> = emptyList()

	override fun reload(repo: NEURepository): Unit {
		val table = MapSerializer(String.serializer(), Reforge.serializer())
		fun load(path: String, what: String): Collection<Reforge> =
			SafeRepoConstants.file(repo, path, what)?.kJson(table)?.values ?: emptyList()
		val everything = load("constants/reforges.json", "reforge recipes and tooltips") +
			load("constants/reforgestones.json", "reforge stone recipes")

		val types = LinkedHashMap<ItemType, MutableList<Reforge>>()
		val names = LinkedHashMap<ItemId, MutableList<Reforge>>()
		val vanilla = LinkedHashMap<ResourceKey<Item>, MutableList<Reforge>>()
		fun <K> LinkedHashMap<K, MutableList<Reforge>>.file(key: K, reforge: Reforge) {
			getOrPut(key) { ArrayList() } += reforge
		}
		for (reforge in everything) for (filter in reforge.eligibleItems) when (filter) {
			is Reforge.Eligibility.OfSkyblockId -> names.file(filter.id, reforge)
			is Reforge.Eligibility.OfVanillaItem -> vanilla.file(filter.key, reforge)
			is Reforge.Eligibility.OfKind -> for (t in resolveItemType(filter.kind)) {
				types.file(t, reforge)
				types.file(t.dungeonVariant, reforge)
			}
		}

		modifierLut = everything.associateBy(Reforge::reforgeId)
		byReforgeStone = LinkedHashMap<ItemId, Reforge>().also { stones ->
			for (reforge in everything) reforge.stoneId?.let { stones[it] = reforge }
		}
		byType = types
		byInternalName = names
		byVanilla = vanilla
		allReforges = everything
	}

	fun <T> NEURepoFile.kJson(
		serializer: KSerializer<T>,
	): T {
		val gsonTree = json(JsonElement::class.java)
		return try {
			Apex.json.decodeFromJsonElement(serializer, JsonOps.INSTANCE.convertTo(KJsonOps.INSTANCE, gsonTree))
		} catch (problem: Exception) {
			throw NEURepositoryException(path, "Could not decode kotlin JSON element", problem)
		}
	}
}
