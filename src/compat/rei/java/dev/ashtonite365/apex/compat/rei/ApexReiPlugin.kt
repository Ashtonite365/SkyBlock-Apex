// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.compat.rei

import dev.architectury.event.EventResult
import java.util.Optional
import me.shedaniel.math.Rectangle
import me.shedaniel.rei.api.client.REIRuntime
import me.shedaniel.rei.api.client.gui.config.SearchFieldLocation
import me.shedaniel.rei.api.client.gui.screen.DisplayScreen
import me.shedaniel.rei.api.client.plugins.REIClientPlugin
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry
import me.shedaniel.rei.api.client.registry.category.visibility.CategoryVisibilityPredicate
import me.shedaniel.rei.api.client.registry.display.DisplayCategory
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones
import me.shedaniel.rei.api.client.registry.screen.ExclusionZonesProvider
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import me.shedaniel.rei.api.common.display.Display
import me.shedaniel.rei.api.common.entry.EntryStack
import me.shedaniel.rei.api.common.entry.type.EntryTypeRegistry
import me.shedaniel.rei.api.common.plugins.REICommonPlugin
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.features.items.recipes.ItemListMods
import dev.ashtonite365.apex.features.items.recipes.PanelRect
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
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.JeiCategoryFilter
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.ItemId

class ApexReiCommonPlugin : REICommonPlugin {
	override fun registerEntryTypes(registry: EntryTypeRegistry) {
		registry.register(ReiEntries.TYPE_ID, SkyblockEntryDefinition)
	}
}

class ApexReiPlugin(
	private val pages: () -> List<ItemListPage<*>> = { ItemListPages.all() },
	private val entries: () -> List<SBItemStack> = { SkyblockEntries.all() },
	private val repoReady: () -> Boolean = { RepoManager.shouldLoadREI() },
	private val usable: () -> Boolean = { ItemListMods.REI.usable() },
) : REIClientPlugin, ItemListNavigator {
	var categories: List<ReiPageCategory<*>> = listOf()
		private set

	var focusCategories: Map<Identifier, ReiPageCategory<*>> = mapOf()
		private set

	private val focused = HashMap<Identifier, ReiPageDisplay<*>>()

	private val registered = mutableListOf<ReiPageDisplay<*>>()

	override fun getPluginProviderName(): String = "SkyBlock Apex"

	override fun registerCategories(registry: CategoryRegistry) {
		if (!usable()) return
		PageMobs.clear()
		val list = pages()
		categories = list.map { ReiPageCategory(it) }
		focusCategories = list.filter { it.id in NAVIGABLE }.associate { it.id to ReiPageCategory(it, focus = true) }
		for (category in categories + focusCategories.values) registry.add(category)
		registry.registerVisibilityPredicate(object : CategoryVisibilityPredicate {
			override fun handleCategory(category: DisplayCategory<*>): EventResult =
				if (hides(category.categoryIdentifier.identifier)) EventResult.interruptFalse() else EventResult.pass()
		})
	}

	override fun registerDisplays(registry: DisplayRegistry) {
		if (!usable()) return
		for (twin in focusCategories.values) {
			@Suppress("UNCHECKED_CAST")
			registry.registerDisplayGenerator(twin.categoryId as me.shedaniel.rei.api.common.category.CategoryIdentifier<Display>,
				object : DynamicDisplayGenerator<Display> {
					override fun generate(builder: ViewSearchBuilder): Optional<List<Display>> {
						if (twin.categoryId !in builder.categories) return Optional.empty()
						val shown = focused[twin.id] ?: return Optional.empty()
						return Optional.of(listOf(shown))
					}
				})
		}
		registry.registerGlobalDisplayGenerator(VanillaStackGenerator { registered.toList() })
		registered.clear()
		if (!repoReady()) return
		for (category in categories) addAll(registry, category)
	}

	private fun <T : Any> addAll(registry: DisplayRegistry, category: ReiPageCategory<T>) {
		Faults.attempt("Failed to register item list pages for ${category.id}") {
			for (recipe in category.page.allRecipes()) {
				val display = category.display(recipe)
				registry.add(display)
				registered += display
			}
		}
	}

	override fun registerEntries(registry: EntryRegistry) {
		if (!usable() || !repoReady()) return
		registry.removeEntryIf { stack: EntryStack<*>? -> ReiEntries.skyblockOf(stack) == null }
		registry.addEntries(entries().map(ReiEntries::stack))
	}

	override fun registerExclusionZones(zones: ExclusionZones) {
		if (!usable()) return
		zones.register(AbstractContainerScreen::class.java, ExclusionZonesProvider<AbstractContainerScreen<*>> { screen ->
			HandledScreenPushREIEvent.publish(HandledScreenPushREIEvent(screen)).rectangles.toList()
		})
		zones.register(Screen::class.java, ExclusionZonesProvider<Screen> { tabBand() })
	}

	override fun registerTransferHandlers(registry: TransferHandlerRegistry) {
		if (!usable()) return
		registry.register(CraftTransfer)
	}

	override fun registerScreens(registry: ScreenRegistry) {
		if (!usable()) return
		ViewerTabs.host = ReiViewerHost
		ItemListNavigation.registerTabs(this)
		ViewerTabs.hook()
	}

	override fun leaveRecipeScreen() {
		val screen = Client.screen ?: return
		if (screen !is DisplayScreen) return
		val back = runCatching { REIRuntime.getInstance().previousContainerScreen }.getOrNull()
		if (back != null) Client.screen = back else screen.onClose()
	}

	override fun openMob(mobId: String?) {
		val ref = mobId?.let(::MobPageRef)
		if (ref?.mob != null && openFocused(MobsPage, ref)) return
		val mobs = categories.firstOrNull { it.page.id == MobsPage.id } ?: return
		ViewSearchBuilder.builder().addCategory(mobs.categoryId).open()
	}

	override fun openItemPage(page: ItemListPage<ItemPageRef>, itemId: String) {
		openFocused(page, ItemPageRef(itemId))
	}

	override fun openRecipesFor(itemId: String) {
		@Suppress("UNCHECKED_CAST")
		val stack = ReiEntries.stack(SBItemStack(ItemId(itemId))) as EntryStack<Any>
		ViewSearchBuilder.builder().addRecipesFor(stack).open()
	}

	fun <T : Any> openFocused(page: ItemListPage<T>, recipe: T): Boolean {
		val twin = focus(page, recipe) ?: return false
		ViewSearchBuilder.builder().addCategory(twin.categoryId).open()
		return true
	}

	fun <T : Any> focus(page: ItemListPage<T>, recipe: T): ReiPageCategory<T>? {
		@Suppress("UNCHECKED_CAST")
		val twin = focusCategories[page.id] as? ReiPageCategory<T> ?: return null
		focused[twin.id] = twin.display(recipe)
		return twin
	}

	fun focusedDisplay(twinId: Identifier): ReiPageDisplay<*>? = focused[twinId]

	private class VanillaStackGenerator(private val displays: () -> List<ReiPageDisplay<*>>) : DynamicDisplayGenerator<Display> {
		private fun lookup(entry: EntryStack<*>, made: Boolean): Optional<List<Display>> {
			if (ReiEntries.skyblockOf(entry) != null) return Optional.empty()
			val item = entry.value as? ItemStack ?: return Optional.empty()
			val skyblock = SkyblockEntries.fromVanilla(item) ?: return Optional.empty()
			val identity = SkyblockEntries.identity(SBItemStack(skyblock))
			val hits: List<Display> = displays().filter { identity in (if (made) it.outputIds else it.inputIds) }
			return Optional.ofNullable(hits.ifEmpty { null })
		}

		override fun getRecipeFor(entry: EntryStack<*>): Optional<List<Display>> = lookup(entry, made = true)
		override fun getUsageFor(entry: EntryStack<*>): Optional<List<Display>> = lookup(entry, made = false)
	}

	object CraftTransfer : TransferHandler {
		override fun handle(context: TransferHandler.Context): TransferHandler.Result {
			val run = craftActionOf(context.display) ?: return TransferHandler.Result.createNotApplicable()
			if (context.isActuallyCrafting) Faults.attempt("Item list craft action failed") { run() }
			return TransferHandler.Result.createSuccessful()
		}

		fun craftActionOf(display: Display?): (() -> Unit)? = (display as? ReiPageDisplay<*>)?.craftAction()
	}

	object ReiViewerHost : ViewerHost {
		private fun runtime(): REIRuntime? = runCatching { REIRuntime.getInstance() }.getOrNull()

		override fun showsPanel(screen: Screen): Boolean =
			RepoManager.shouldLoadREI() && runtime()?.isOverlayVisible == true &&
				(screen is AbstractContainerScreen<*> || screen is DisplayScreen)

		override fun panelRect(screen: Screen): PanelRect? = runCatching {
			val rt = runtime() ?: return null
			val column = rt.overlay.orElse(null)?.bounds ?: return null
			val list = rt.calculateEntryListArea(column)
			val bottom = if (rt.contextualSearchFieldLocation == SearchFieldLocation.BOTTOM_SIDE)
				column.maxY - ViewerTabs.SEARCH_BAR_H - ViewerTabs.SEARCH_LIFT
			else list.maxY - ViewerTabs.SEARCH_LIFT
			PanelRect(list.x, list.maxX, bottom)
		}.getOrNull()

		override fun hoveredItemId(screen: Screen): String? {
			if (ReiSearch.focused()) return null
			val stack = ReiSearch.hoveredEntry()?.let(ReiEntries::skyblockOf) ?: return null
			return stack.catalogItemId.skyblockId.takeIf { it.isNotEmpty() }
		}
	}

	companion object {

		val NAVIGABLE: Set<Identifier> = setOf(ItemListPages.MOBS, ItemListPages.HOW_TO, ItemListPages.PRICES)

		fun hides(category: Identifier): Boolean = JeiCategoryFilter.hide(category.namespace, Apex.MOD_ID)

		fun tabBand(): List<Rectangle> =
			ViewerTabs.currentExclusion()?.let { listOf(Rectangle(it.x, it.y, it.w, it.h)) } ?: listOf()
	}
}
