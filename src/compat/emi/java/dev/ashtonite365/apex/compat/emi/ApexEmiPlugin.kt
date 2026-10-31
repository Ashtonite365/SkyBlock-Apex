// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.emi

import com.google.auto.service.AutoService
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.EmiPlugin
import dev.emi.emi.api.EmiRegistry
import dev.emi.emi.api.stack.EmiStackInteraction
import dev.emi.emi.api.widget.Bounds
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.features.items.recipes.ItemListMods
import dev.ashtonite365.apex.features.items.recipes.ViewerHost
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListNavigation
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListNavigator
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemListPages
import dev.ashtonite365.apex.features.items.recipes.itemlist.ItemPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobPageRef
import dev.ashtonite365.apex.features.items.recipes.itemlist.MobsPage
import dev.ashtonite365.apex.features.items.recipes.itemlist.PageMobs
import dev.ashtonite365.apex.features.items.recipes.itemlist.SkyblockEntries
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.JeiCategoryFilter
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.HoverStackSource
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.ItemListInputBridge
import dev.ashtonite365.apex.util.ItemListSearchProvider
import dev.ashtonite365.apex.util.compatloader.CompatLoader
import dev.ashtonite365.apex.util.mc.NeedsComponents

class ApexEmiPlugin(
	private val pages: () -> List<ItemListPage<*>> = { ItemListPages.all() },
	private val entries: () -> List<SBItemStack> = { SkyblockEntries.all() },
	private val repoReady: () -> Boolean = { RepoManager.shouldLoadREI() },
	private val usable: () -> Boolean = { ItemListMods.EMI.usable() },

	private val viewerTabs: Boolean = true,
) : EmiPlugin, ItemListNavigator {
	var categories: List<EmiPageCategory> = listOf()
		private set

	override fun register(registry: EmiRegistry) {
		if (!usable()) return
		PageMobs.clear()
		val all = pages()
		categories = all.map { EmiPageCategory(it) }
		categories.forEach(registry::addCategory)

		registry.removeRecipes { hides(it.category.id) }
		registry.addGenericExclusionArea { screen, out -> exclusions(screen).forEach(out::accept) }
		registry.addGenericStackProvider { screen, _, _ -> hoveredInventoryStack(screen) }
		if (viewerTabs) installViewer()
		if (!repoReady()) return
		registry.removeEmiStacks { it !is SkyblockEmiStack }
		entries().forEach { registry.addEmiStack(EmiEntries.stack(it)) }
		for (category in categories) addAll(registry, category)
	}

	private fun addAll(registry: EmiRegistry, category: EmiPageCategory) {
		Faults.attempt("Failed to register item list pages for ${category.id}") {
			recipesOf(category).forEach(registry::addRecipe)
		}
	}

	fun recipesOf(category: EmiPageCategory): List<EmiPageRecipe<*>> = pageRecipes(category.page, category)

	private fun <T : Any> pageRecipes(page: ItemListPage<T>, category: EmiPageCategory): List<EmiPageRecipe<T>> =
		page.allRecipes().mapIndexed { i, r -> EmiPageRecipe(page, r, category, i) }

	private fun installViewer() {
		ViewerTabs.host = EmiViewerHost
		ItemListNavigation.registerTabs(this)
		ViewerTabs.hook()
	}

	override fun leaveRecipeScreen() {
		val screen = Client.screen ?: return
		if (!isRecipeScreen(screen)) return
		EmiApi.getHandledScreen()?.let { Client.screen = it } ?: screen.onClose()
	}

	override fun openMob(mobId: String?) {
		if (mobId != null && MobPageRef(mobId).mob != null && openFocused(MobsPage, MobPageRef(mobId))) return
		categoryOf(MobsPage)?.let(EmiApi::displayRecipeCategory)
	}

	override fun openItemPage(page: ItemListPage<ItemPageRef>, itemId: String) {
		openFocused(page, ItemPageRef(itemId))
	}

	override fun openRecipesFor(itemId: String) {
		EmiApi.displayRecipes(EmiEntries.stack(SBItemStack(ItemId(itemId))))
	}

	fun <T : Any> focusedRecipe(page: ItemListPage<T>, recipe: T): EmiPageRecipe<T>? =
		categoryOf(page)?.let { EmiPageRecipe(page, recipe, it, FOCUS_INDEX) }

	private fun <T : Any> openFocused(page: ItemListPage<T>, recipe: T): Boolean {
		val r = focusedRecipe(page, recipe) ?: return false
		EmiApi.displayRecipe(r)
		return true
	}

	private fun categoryOf(page: ItemListPage<*>): EmiPageCategory? = categories.firstOrNull { it.page.id == page.id }

	object EmiViewerHost : ViewerHost {
		override fun showsPanel(screen: Screen): Boolean =
			RepoManager.shouldLoadREI() && (screen is AbstractContainerScreen<*> || isRecipeScreen(screen))

		override fun hoveredItemId(screen: Screen): String? {
			if (EmiSearch.focused()) return null
			return EmiSearch.hoveredSkyblock()?.catalogItemId?.skyblockId?.takeIf { it.isNotEmpty() }
		}
	}

	companion object {

		const val FOCUS_INDEX = 999_999

		fun hides(category: Identifier): Boolean = JeiCategoryFilter.hide(category.namespace, Apex.MOD_ID)

		fun isRecipeScreen(screen: Screen): Boolean = screen.javaClass.name.startsWith("dev.emi.emi")

		fun exclusions(screen: Screen): List<Bounds> {
			val out = mutableListOf<Bounds>()
			if (screen is AbstractContainerScreen<*>)
				HandledScreenPushREIEvent.publish(HandledScreenPushREIEvent(screen)).rectangles
					.forEach { out += Bounds(it.x, it.y, it.width, it.height) }
			ViewerTabs.currentExclusion()?.let { out += Bounds(it.x, it.y, it.w, it.h) }
			return out
		}

		fun hoveredInventoryStack(screen: Screen): EmiStackInteraction {
			if (!RepoManager.shouldLoadREI()) return EmiStackInteraction.EMPTY
			val item = (screen as? AccessorHandledScreen)?.focusedSlot_Apex?.item?.takeUnless { it.isEmpty }
				?: return EmiStackInteraction.EMPTY
			val id = SkyblockEntries.fromVanilla(item) ?: return EmiStackInteraction.EMPTY
			return EmiStackInteraction(EmiEntries.stack(SBItemStack(id)))
		}
	}
}

object EmiSearch {
	private val ok get() = ItemListMods.EMI.usable()
	fun focused(): Boolean = ok && runCatching { EmiApi.isSearchFocused() }.getOrDefault(false)
	fun text(): String? = if (!ok) null else runCatching { EmiApi.getSearchText() }.getOrNull()
	fun hoveredSkyblock(): SBItemStack? = if (!ok) null else runCatching {
		EmiEntries.skyblockOf(EmiApi.getHoveredStack(false).stack)
	}.getOrNull()
	fun hoveredVanilla(): ItemStack? = if (!ok) null else runCatching {
		EmiApi.getHoveredStack(false).stack.emiStacks.firstOrNull()?.takeUnless { it is SkyblockEmiStack }?.itemStack?.takeUnless { it.isEmpty }
	}.getOrNull()
}

@AutoService(HoverStackSource::class)
@CompatLoader.RequireMod("emi")
class EmiHoverSource : HoverStackSource {
	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	override fun stackUnderMouse(screen: Screen): ItemStack? =
		EmiSearch.hoveredSkyblock()?.asImmutableItemStack()?.rebuildLive() ?: EmiSearch.hoveredVanilla()

	override val sourcePriority: Int get() = 1
}

@AutoService(ItemListInputBridge::class)
@CompatLoader.RequireMod("emi")
class EmiInputBridge : ItemListInputBridge {
	override fun hasKeyboardFocus(): Boolean = EmiSearch.focused()
}

@AutoService(ItemListSearchProvider::class)
@CompatLoader.RequireMod("emi")
class EmiSearchProvider : ItemListSearchProvider {
	override fun currentSearchText(): String? = EmiSearch.text()
}
