// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes.itemlist

import io.github.moulberry.repo.data.NEUCraftingRecipe
import io.github.moulberry.repo.data.NEUNpcShopRecipe
import me.shedaniel.math.Point
import me.shedaniel.math.Rectangle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.items.recipes.AcquisitionInfo
import dev.ashtonite365.apex.features.items.recipes.HowToGet
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import dev.ashtonite365.apex.features.menus.craft.CraftHandoff
import dev.ashtonite365.apex.features.menus.craft.CraftRequest
import dev.ashtonite365.apex.repo.BoxContents
import dev.ashtonite365.apex.repo.BoxContentsLayout
import dev.ashtonite365.apex.repo.PetVariants
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.mobs.MobEntry
import dev.ashtonite365.apex.repo.mobs.MobIndex
import dev.ashtonite365.apex.repo.mobs.MobText
import dev.ashtonite365.apex.repo.recipes.GenericRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.RecipeLayouter.SlotRole
import dev.ashtonite365.apex.repo.recipes.SBCraftingRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBEssenceUpgradeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBForgeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBKatUpgradeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBReforgeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBTradeRecipeRenderer
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.KeptStack
import dev.ashtonite365.apex.util.mc.setCustomName

data class MobPageRef(val mobId: String) {
	val mob: MobEntry? get() = MobIndex.get(mobId)
}
data class ItemPageRef(val itemId: String)
data class BoxPageRef(val box: String, val page: Int)
data class PetBaseRef(val base: String)

private fun text(s: String): Component = Component.literal(s)
private fun stackOf(id: String, count: Int = 1) = SBItemStack(ItemId(id), count)
private fun Rectangle.at(dx: Int, dy: Int) = Point(minX + dx, minY + dy)

object ItemListPages {
	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(Apex.MOD_ID, path)

	private val renderers: List<GenericRecipeRenderer<*>> = listOf(
		SBCraftingRecipeRenderer, SBForgeRecipeRenderer, SBEssenceUpgradeRecipeRenderer,
		SBReforgeRecipeRenderer, SBTradeRecipeRenderer, SBKatUpgradeRecipeRenderer,
	)

	fun all(): List<ItemListPage<*>> =
		renderers.map { RendererPage(it) } +
			listOf(MobDropsPage, MobsPage, HowToPage, PricesPage, ShopPage, ContentsPage, PetRaritiesPage)

	val MOBS = id("skyblock_mobs")
	val HOW_TO = id("how_to_get")
	val PRICES = id("live_price")
	val CRAFTING: Identifier get() = SBCraftingRecipeRenderer.identifier
}

class RendererPage<T : Any>(val renderer: GenericRecipeRenderer<T>) : ItemListPage<T> {
	override val id: Identifier get() = renderer.identifier
	override val title: Component get() = renderer.title
	override val icon: ItemStack get() = renderer.icon
	override val width: Int get() = renderer.displayWidth
	override val height: Int get() = renderer.displayHeight
	override fun allRecipes(): List<T> = renderer.findAllRecipes(RepoManager.coreRepo).toList()
	override fun draw(recipe: T, bounds: Rectangle, canvas: PageCanvas) = renderer.render(recipe, bounds, canvas, null)
	override fun inputs(recipe: T): List<List<SBItemStack>> = renderer.getInputs(recipe).map { listOf(it) }
	override fun outputs(recipe: T): List<List<SBItemStack>> = renderer.getOutputs(recipe).map { listOf(it) }

	override fun craftAction(recipe: T): (() -> Unit)? =
		(recipe as? NEUCraftingRecipe)?.let { crafting -> { CraftHandoff.request(CraftRequest.of(crafting)) } }
}

object ShopPage : ItemListPage<NEUNpcShopRecipe> {
	override val id = ItemListPages.id("npc_shopping")
	override val title: Component get() = text("SkyBlock NPC Shopping")
	override val icon: ItemStack get() = ItemStack(Items.EMERALD)
	override val width = 150
	override val height = 66

	override fun allRecipes(): List<NEUNpcShopRecipe> =
		RepoManager.getAllRecipes().filterIsInstance<NEUNpcShopRecipe>().toList()

	override fun draw(recipe: NEUNpcShopRecipe, bounds: Rectangle, canvas: PageCanvas) {
		val npc = bounds.at(64, 9)
		canvas.itemSlotAt(npc.x, npc.y, stackOf(recipe.isSoldBy.skyblockItemId), SlotRole.DISPLAY)
		canvas.arrowAt(bounds.minX + 61, bounds.minY + 27)
		val firstY = 24 - 9 * (recipe.cost.size - 1)
		for ((row, cost) in recipe.cost.withIndex())
			canvas.itemSlotAt(bounds.minX + 43, bounds.minY + firstY + 18 * row, SBItemStack(cost), SlotRole.SMALL_INPUT)
		canvas.itemSlotAt(bounds.minX + 93, bounds.minY + 24, SBItemStack(recipe.result), SlotRole.BIG_OUTPUT)
	}
}

object ContentsPage : ItemListPage<BoxPageRef> {
	override val id = ItemListPages.id("box_contents")
	override val title: Component get() = text("Contents")
	override val icon: ItemStack get() = ItemStack(Items.CHEST)
	override val width = 160
	override val height = 130
	const val BODY_TOP = 24

	private fun layoutOf(box: BoxContents.Box) = BoxContentsLayout.pages(box, height - BODY_TOP, width - 8, ::measure)
	private fun measure(s: String): Int = runCatching { dev.ashtonite365.apex.util.Client.font.width(s) }.getOrElse { BoxContentsLayout.roughWidth(s) }

	override fun allRecipes(): List<BoxPageRef> =
		BoxContents.boxes.values.flatMap { box -> List(layoutOf(box).size) { BoxPageRef(box.box, it) } }

	private fun dropStack(drop: BoxContents.Drop, box: BoxContents.Box): SBItemStack {
		val amount = drop.max.toInt().coerceAtLeast(1)
		val base = when (drop.type) {
			BoxContents.Type.ITEM -> stackOf(drop.id!!, amount)
			BoxContents.Type.COINS -> SBItemStack(ItemId.COINS, amount)
			BoxContents.Type.SKILL_XP -> {
				val skill = drop.skill!!
				val shown = skill.lowercase().replaceFirstChar { it.uppercase() }
				stackOf("SKYBLOCK_SKILL_" + skill.uppercase()).copy(
					fallback = KeptStack.build(Items.EXPERIENCE_BOTTLE) { setCustomName(text("§3$shown XP")) })
			}
		}
		return base.copy(extraLore = BoxContents.dropLines(drop, box).map(::text))
	}

	override fun draw(recipe: BoxPageRef, bounds: Rectangle, canvas: PageCanvas) {
		val box = BoxContents.boxes[recipe.box] ?: return
		val pages = layoutOf(box)
		canvas.itemSlotAt(bounds.minX + 4, bounds.minY + 4, stackOf(box.box), SlotRole.BIG_OUTPUT)
		val name = BoxContentsLayout.headerName(box)
		val suffix = if (pages.size > 1) " §8(${recipe.page + 1}/${pages.size})" else ""
		val head = dev.ashtonite365.apex.features.menus.MenuTheme.fit(name, width - 36 - measure(suffix), ::measure) + suffix
		canvas.placeLabel(BoxContentsLayout.leftLabelX(bounds.minX + 32, head, ::measure), bounds.minY + 8, text(head))
		var y = bounds.minY + BODY_TOP
		for (line in pages.getOrNull(recipe.page) ?: emptyList()) {
			if (line is BoxContentsLayout.Label) canvas.placeLabel(BoxContentsLayout.leftLabelX(bounds.minX + 4, line.text, ::measure), y + 1, text(line.text))
			if (line is BoxContentsLayout.Drops) for ((i, drop) in line.drops.withIndex())
				canvas.itemSlotAt(bounds.minX + 4 + 18 * i, y, dropStack(drop, box), SlotRole.SMALL_INPUT)
			y += line.h
		}
	}
}

object PetRaritiesPage : ItemListPage<PetBaseRef> {
	override val id = ItemListPages.id("pet_rarities")
	override val title: Component get() = text("Pet Rarities")
	override val icon: ItemStack get() = ItemStack(Items.BONE)
	override val width = 160
	override val height = 60

	override fun allRecipes(): List<PetBaseRef> = PetVariants.current.variants.keys.map(::PetBaseRef)

	override fun draw(recipe: PetBaseRef, bounds: Rectangle, canvas: PageCanvas) {
		val variants = PetVariants.current.variants[recipe.base] ?: return
		val stacks = variants.map { SBItemStack(ItemId(it)) }
		canvas.cyclingSlotAt(bounds.minX + 4, bounds.minY + 4, stacks, SlotRole.BIG_OUTPUT)
		canvas.placeLabel(bounds.minX + 32, bounds.minY + 8, text("${variants.size} rarities §8(cycling)"))
		for ((i, s) in stacks.withIndex()) canvas.itemSlotAt(bounds.minX + 4 + 18 * i, bounds.minY + 34, s, SlotRole.SMALL_OUTPUT)
	}
}

internal fun mobTooltip(mob: MobEntry): List<Component> = MobText.hover(mob).map(::text)

internal fun dropStacks(mob: MobEntry): List<SBItemStack> = mob.drops.map { drop ->
	val lore = drop.extra + listOfNotNull(drop.chance?.let { "§7Chance: §e$it" })
	stackOf(drop.itemId, drop.count).copy(extraLore = lore.map(::text))
}

private fun PageCanvas.grid(stacks: List<SBItemStack>, left: Int, top: Int, perRow: Int, role: SlotRole) {
	for ((i, s) in stacks.withIndex()) itemSlotAt(left + 18 * (i % perRow), top + 18 * (i / perRow), s, role)
}

object MobsPage : ItemListPage<MobPageRef> {
	override val id = ItemListPages.MOBS
	override val title: Component get() = text("SkyBlock Mobs")
	override val icon: ItemStack get() = ItemStack(Items.ZOMBIE_HEAD)
	override val width = 160
	override val height = 200

	override fun allRecipes(): List<MobPageRef> = MobIndex.all().map { MobPageRef(it.id) }

	override fun draw(recipe: MobPageRef, bounds: Rectangle, canvas: PageCanvas) {
		val mob = recipe.mob ?: return
		val (left, top) = bounds.minX to bounds.minY
		canvas.mobPreview(mob, Rectangle(left + 4, top + 4, 50, 80), mobTooltip(mob))
		canvas.wrappedText(left + 58, top + 4, width - 62, listOf(text(mob.name)))
		val card = MobText.cardLines(mob)
		if (card.isNotEmpty()) canvas.wrappedText(left + 4, top + 88, width - 8, card.map(::text))
		canvas.grid(dropStacks(mob), left + 58, top + 16, 5, SlotRole.DISPLAY)
		if (mob.hostItems.isNotEmpty()) canvas.lookupItems(mob.hostItems.map { stackOf(it) })
	}
}

object MobDropsPage : ItemListPage<MobPageRef> {
	override val id = ItemListPages.id("mob_drop_recipe")
	override val title: Component get() = text("Mob Drops")
	override val icon: ItemStack get() = ItemStack(Items.DIAMOND_SWORD)
	override val width = 150
	override val height = 100

	override fun allRecipes(): List<MobPageRef> = MobIndex.all().filter { it.drops.isNotEmpty() }.map { MobPageRef(it.id) }

	override fun draw(recipe: MobPageRef, bounds: Rectangle, canvas: PageCanvas) {
		val mob = recipe.mob ?: return
		val (left, top) = bounds.minX to bounds.minY
		canvas.mobPreview(mob, Rectangle(left + 5, top + 15, 50, 80), mobTooltip(mob) + text("§eClick to open in the Mobs tab"))
		canvas.wrappedText(left + 5, top + 4, width - 10, listOf(text(mob.name)))
		canvas.clickArea(Rectangle(left, top, 56, height)) { ViewerTabs.navigate("mobs", recipe.mobId) }
		canvas.grid(dropStacks(mob), left + 60, top + 20, 4, SlotRole.SMALL_OUTPUT)
	}
}

object HowToPage : ItemListPage<ItemPageRef> {
	override val id = ItemListPages.HOW_TO
	override val title: Component get() = text("How to get")
	override val icon: ItemStack get() = ItemStack(Items.COMPASS)
	override val width = 170
	override val height = 160

	override fun allRecipes(): List<ItemPageRef> = AcquisitionInfo.itemsWithPage().map(::ItemPageRef)

	private fun heading(s: String) = PageLine(text("§l$s"))
	private fun note(s: String) = PageLine(text("§7$s"))
	private fun link(s: String, action: () -> Unit) = PageLine(text("§b> $s"), action)

	fun lines(itemId: String): List<PageLine> = buildList {
		AcquisitionInfo.forItem(itemId)?.let { page ->
			add(heading("How to obtain"))
			page.text.forEach { add(note(it)) }
			page.wikiUrl?.let { url -> add(link("Open wiki page") { PageActions.confirmLink(url) }) }
		}
		val model = HowToGet.forItem(itemId)
		add(heading("Crafting / recipes"))
		if (model.recipeGroups.isEmpty()) add(note("No recipe produces this item"))
		for (group in model.recipeGroups)
			add(link("${group.kind.label} (${group.count})") { ItemListNavigation.openRecipesFor(itemId) })
		add(heading("Mob drops"))
		if (model.mobIds.isEmpty()) add(note("Not dropped by any known mob"))
		for (mobId in model.mobIds) {
			val mob = MobIndex.get(mobId)
			val chance = mob?.drops?.firstOrNull { it.itemId == itemId }?.chance
			val label = (mob?.plainName ?: mobId) + (chance?.let { " §7($it)" } ?: "")
			add(link(label) { ViewerTabs.navigate("mobs", mobId) })
		}
		add(heading("Bazaar / AH"))
		val price = HowToGet.livePrice(itemId)
		val shown = if (price.hasAny) price.lines().first() else "No live price"
		add(link(shown) { ViewerTabs.navigate("prices", itemId) })
	}

	override fun draw(recipe: ItemPageRef, bounds: Rectangle, canvas: PageCanvas) {
		val role = if (AcquisitionInfo.forItem(recipe.itemId) != null) SlotRole.SMALL_OUTPUT else SlotRole.DISPLAY
		canvas.itemSlotAt(bounds.minX + 4, bounds.minY + 4, stackOf(recipe.itemId), role)
		canvas.scrollLines(Rectangle(bounds.minX + 4, bounds.minY + 24, width - 8, height - 26), lines(recipe.itemId))
	}
}

object PricesPage : ItemListPage<ItemPageRef> {
	override val id = ItemListPages.PRICES
	override val title: Component get() = text("Bazaar / AH price")
	override val icon: ItemStack get() = ItemStack(Items.GOLD_INGOT)
	override val width = 170
	override val height = 90

	override fun allRecipes(): List<ItemPageRef> = emptyList()

	override fun draw(recipe: ItemPageRef, bounds: Rectangle, canvas: PageCanvas) {
		canvas.itemSlotAt(bounds.minX + 4, bounds.minY + 4, stackOf(recipe.itemId), SlotRole.DISPLAY)
		val textWidth = width - 8
		val price = HowToGet.livePrice(recipe.itemId)
		val shown = if (price.hasAny) price.lines() else listOf("No live price")
		var top = bounds.minY + 24
		for (line in shown) {
			val c = text(line)
			canvas.wrappedText(bounds.minX + 4, top, textWidth, listOf(c))
			top += 10 * PageText.rows(c, textWidth)
		}
		canvas.wrappedText(bounds.minX + 4, top, textWidth, listOf(text("§b> How to get this item")))
		canvas.clickArea(Rectangle(bounds.minX + 4, top, textWidth, 10)) { ViewerTabs.navigate("howto", recipe.itemId) }
	}
}
