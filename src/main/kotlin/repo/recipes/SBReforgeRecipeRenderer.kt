// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo.recipes

import io.github.moulberry.repo.NEURepository
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.gui.entity.EntityRenderer
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.Reforge
import dev.ashtonite365.apex.repo.ReforgeStore
import dev.ashtonite365.apex.repo.RepoItemTypeCache
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ApexFormatters.withThousands
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.gold
import dev.ashtonite365.apex.util.grey
import dev.ashtonite365.apex.util.skyblock.Rarity
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.catalogItemId
import dev.ashtonite365.apex.util.tr

object SBReforgeRecipeRenderer
	: GenericRecipeRenderer<Reforge> {
	private const val COLUMN = 24

	override val typ get() = Reforge::class.java
	override val icon: ItemStack by lazy { Blocks.ANVIL.asItem().defaultInstance }
	override val title: Component get() = tr("apex.recipecategory.reforge", "Reforge")
	override val identifier: Identifier get() = Apex.identifier("reforge_recipe")

	@OptIn(ExpensiveItemCacheApi::class)
	override fun render(recipe: Reforge, bounds: Rectangle, layouter: RecipeLayouter, mainItem: SBItemStack?) {
		val x0 = bounds.minX + 10
		val slotY = bounds.centerY - 9
		layouter.cyclingSlotAt(x0, slotY, mainItem?.let { listOf(it) } ?: candidatesFor(recipe), RecipeLayouter.SlotRole.SMALL_INPUT)
		layouter.itemSlotAt(x0 + 2 * COLUMN, slotY, null, RecipeLayouter.SlotRole.SMALL_OUTPUT)

		recipe.statUniverse.forEachIndexed { row, _ ->
			layouter.placeLabel(x0 + 2 * COLUMN + 20, bounds.minY + 8 + row * 11, Component.empty())
		}
		val stone = recipe.stoneId
		if (stone == null) {
			showBlacksmith(layouter, x0 + COLUMN + 8, bounds.centerY)
			return
		}
		layouter.itemSlotAt(x0 + COLUMN, slotY - 10, SBItemStack(stone), RecipeLayouter.SlotRole.SMALL_INPUT)
		val anvil = Rectangle(x0 + COLUMN, slotY + 10, 16, 16)
		layouter.itemSlotAt(anvil.x, anvil.y, SBItemStack(SkyBlockItems.REFORGE_ANVIL), RecipeLayouter.SlotRole.DISPLAY)
		val costs = recipe.costByTier
		val costLines = ArrayList<Component>()
		for (tier in Rarity.entries) {
			val coins = costs?.get(tier) ?: continue
			costLines += Component.empty().append(tier.text).append(": ").append(Component.literal(withThousands(coins, 0) + " Coins").gold())
		}
		layouter.hoverArea(anvil, costLines)
	}

	private fun showBlacksmith(layouter: RecipeLayouter, centreX: Int, centreY: Int) {
		val size = EntityRenderer.defaultSize
		val area = Rectangle(Point(centreX - size.width / 2, centreY - size.height / 2), size)
		val smith = EntityTypes.VILLAGER.create(EntityRenderer.fakeWorld, EntitySpawnReason.COMMAND)
		if (smith != null) {
			smith.villagerData = smith.villagerData.withProfession(Client.registries, VillagerProfession.WEAPONSMITH)
			layouter.entityIn(area, smith)
		}
		layouter.hoverArea(area, tr("apex.recipecategory.reforge.basic", "This is a basic reforge, available at the Blacksmith.").grey())
	}

	private fun candidatesFor(recipe: Reforge): List<SBItemStack> {
		val result = ArrayList<SBItemStack>()
		for (filter in recipe.eligibleItems) when (filter) {
			is Reforge.Eligibility.OfSkyblockId -> result += SBItemStack(filter.id)
			is Reforge.Eligibility.OfVanillaItem -> Unit
			is Reforge.Eligibility.OfKind -> run {
				val types = LinkedHashSet<dev.ashtonite365.apex.util.skyblock.ItemType>()
				for (t in ReforgeStore.resolveItemType(filter.kind)) { types += t; types += t.dungeonVariant }
				val repoItems = LinkedHashSet<io.github.moulberry.repo.data.NEUItem>()
				for (t in types) RepoItemTypeCache.byItemType[t]?.let(repoItems::addAll)
				repoItems.mapTo(result) { SBItemStack(it.catalogItemId) }
			}
		}
		return result
	}

	override fun getInputs(recipe: Reforge): Collection<SBItemStack> = recipe.stoneId?.let { listOf(SBItemStack(it)) }.orEmpty()

	override fun getOutputs(recipe: Reforge): Collection<SBItemStack> = emptyList()

	override fun findAllRecipes(coreRepository: NEURepository): Iterable<Reforge> = ReforgeStore.allReforges
}
