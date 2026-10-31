/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * APEX-26.3 PORT: JEI plugin replacing ApexReiPlugin (REI was dropped at the user's request).
 * Provides: the SkyBlock item list (custom ingredient type, vanilla items hidden like the REI plugin did),
 * crafting/forge/essence/reforge/mob-drop/NPC-shop recipe categories, and exclusion zones.
 * Not ported (REI-only features): Kat upgrade category, collapsible item groups, the REI transfer handler
 * (super-craft / /viewrecipe), focused-stack provider.
 */
package dev.ashtonite365.apex.compat.jei

import mezz.jei.api.IModPlugin
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.handlers.IGuiContainerHandler
import mezz.jei.api.registration.IGuiHandlerRegistration
import mezz.jei.api.registration.IModIngredientRegistration
import mezz.jei.api.registration.IRecipeCategoryRegistration
import mezz.jei.api.registration.IRecipeRegistration
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.Rect2i
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.repo.recipes.SBCraftingRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBEssenceUpgradeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBForgeRecipeRenderer
import dev.ashtonite365.apex.repo.recipes.SBReforgeRecipeRenderer
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.features.items.recipes.ViewerGridRows
import dev.ashtonite365.apex.features.items.recipes.ViewerHost
import dev.ashtonite365.apex.features.items.recipes.ViewerTab
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.catalogItemId

class ApexJeiPlugin : IModPlugin {
	init { Faults.attempt("Item list style") { dev.ashtonite365.apex.compat.jei.style.JeiStylePanel.install() } }
	override fun getPluginUid(): Identifier = Identifier.fromNamespaceAndPath(Apex.MOD_ID, "jei_plugin")

	private var categories: List<LayouterCategory<*>> = listOf()

	private fun allSkyblockItems(): List<SBItemStack> =

		RepoManager.coreRepo.items?.items?.keys?.let { dev.ashtonite365.apex.repo.PetVariants.current.merged(it) }
			?.map { SBItemStack(dev.ashtonite365.apex.util.ItemId(it)) } ?: listOf()

	override fun registerIngredients(registration: IModIngredientRegistration) {
		val items = if (RepoManager.shouldLoadREI()) runCatching { allSkyblockItems() }.getOrElse { listOf() } else listOf()
		registration.register(SBItemIngredientType, items, SBItemIngredientHelper, SBItemIngredientRenderer, SBItemCodec)
	}

	override fun registerCategories(registration: IRecipeCategoryRegistration) {
		val gh = registration.jeiHelpers.guiHelper
		categories = listOf(
			GenericJeiCategory(SBCraftingRecipeRenderer, gh),
			GenericJeiCategory(SBForgeRecipeRenderer, gh),
			GenericJeiCategory(SBEssenceUpgradeRecipeRenderer, gh),
			GenericJeiCategory(SBReforgeRecipeRenderer, gh),
			GenericJeiCategory(dev.ashtonite365.apex.repo.recipes.SBTradeRecipeRenderer, gh),
			GenericJeiCategory(dev.ashtonite365.apex.repo.recipes.SBKatUpgradeRecipeRenderer, gh),
			MobDropRefJeiCategory(gh),
			MobsJeiCategory(gh),
			HowToJeiCategory(gh),
			PricesJeiCategory(gh),
			ShopJeiCategory(gh),
			ContentsJeiCategory(gh),
			PetRaritiesJeiCategory(gh),
		)
		registration.addRecipeCategories(*categories.toTypedArray())
	}

	private val vanillaBridge = JeiVanillaStackBridge { categories.map { it.type } }

	override fun registerAdvanced(registration: mezz.jei.api.registration.IAdvancedRegistration) {
		vanillaBridge.focusFactory = registration.jeiHelpers.focusFactory
		registration.addRecipeManagerPlugin(vanillaBridge)
	}

	override fun registerRecipes(registration: IRecipeRegistration) {
		if (!RepoManager.shouldLoadREI()) return
		for (category in categories) addAll(registration, category)
	}

	private fun <T : Any> addAll(registration: IRecipeRegistration, category: LayouterCategory<T>) {
		try {
			registration.addRecipes(category.type, category.allRecipes())
		} catch (ex: Exception) {
			Faults.reportFault("Failed to register item list recipes for ${category.type.uid}", ex)
		}
	}

	override fun registerRecipeTransferHandlers(registration: mezz.jei.api.registration.IRecipeTransferRegistration) {
		@Suppress("UNCHECKED_CAST")
		val crafting = categories.firstOrNull { (it as? GenericJeiCategory<*>)?.renderer === SBCraftingRecipeRenderer }
			as? GenericJeiCategory<io.github.moulberry.repo.data.NEUCraftingRecipe> ?: return
		ApexJeiTransfer.register(registration, crafting.type)
	}

	private var jeiCfgStamp = Long.MIN_VALUE
	private var jeiCfg = ViewerTabs.JeiListConfig()

	private fun jeiListConfig(): ViewerTabs.JeiListConfig {
		val f = net.fabricmc.loader.api.FabricLoader.getInstance().configDir.resolve("jei/client/default/jei-client.ini")
		val stamp = runCatching { java.nio.file.Files.getLastModifiedTime(f).toMillis() }.getOrDefault(-1L)
		if (stamp != jeiCfgStamp) {
			jeiCfgStamp = stamp
			jeiCfg = if (stamp < 0) ViewerTabs.JeiListConfig()
			else runCatching { ViewerTabs.JeiListConfig.parse(java.nio.file.Files.readString(f)) }.getOrDefault(ViewerTabs.JeiListConfig())
		}
		return jeiCfg
	}

	private var rowLimitNoted = ""

	private fun noteRowLimit(stripTop: Int) {
		val cfg = jeiListConfig()
		val lost = ViewerTabs.rowsLostToMaxRows(stripTop, cfg)
		val key = "$stripTop/${cfg.maxRows}"
		if (lost <= 0 || key == rowLimitNoted) return
		rowLimitNoted = key
		Apex.logger.info("[ItemList] [ingredientList] maxRows = ${cfg.maxRows}, but ${ViewerTabs.rowsThatFit(stripTop, cfg)} rows fit above the tabs; " +
			"the item list fills them while the tabs are shown (config not changed).")
	}

	private fun registerViewerTabs(rt: IJeiRuntime) {
		val mobsCategory = categories.filterIsInstance<MobsJeiCategory>().firstOrNull() ?: return
		fun isRecipesScreen(s: net.minecraft.client.gui.screens.Screen?) = s != null && s.javaClass.name.startsWith("mezz.jei")
		ViewerTabs.host = object : ViewerHost {
			override fun showsPanel(screen: net.minecraft.client.gui.screens.Screen): Boolean =
				RepoManager.shouldLoadREI() && rt.ingredientListOverlay.isListDisplayed &&
					(screen is AbstractContainerScreen<*> || isRecipesScreen(screen))

			override fun panelRect(screen: net.minecraft.client.gui.screens.Screen) =
				rt.screenHelper.getGuiProperties(screen).map {
					ViewerTabs.panelFromJeiLayout(it.guiRight(), it.guiTop() + it.guiYSize(), it.screenWidth(), it.screenHeight(), jeiListConfig())
						?: ViewerTabs.panelFromGui(it.guiRight(), it.screenWidth(), it.screenHeight())
				}.orElse(null)

			override fun hoveredItemId(screen: net.minecraft.client.gui.screens.Screen): String? =
				if (rt.ingredientListOverlay.hasKeyboardFocus()) null
				else (rt.ingredientListOverlay.getIngredientUnderMouse(SBItemIngredientType)
					?: rt.bookmarkOverlay.getIngredientUnderMouse(SBItemIngredientType)
					?: rt.recipesGui.getIngredientUnderMouse(SBItemIngredientType).orElse(null))
					?.catalogItemId?.skyblockId?.takeIf { it.isNotEmpty() }
		}
		ViewerTabs.register(ViewerTab("items", Component.literal("Items"), 0, { _ ->

			val cur = dev.ashtonite365.apex.util.Client.screen
			if (isRecipesScreen(cur)) rt.recipesGui.parentScreen.ifPresent { dev.ashtonite365.apex.util.Client.screen = it }
		}))
		ViewerTabs.register(ViewerTab("mobs", Component.literal("Mobs"), 10, { entryId ->
			val ref = entryId?.let { MobRef(it) }?.takeIf { it.mob != null }
			if (ref != null) rt.recipesGui.showRecipes(mobsCategory, listOf(ref), listOf())
			else rt.recipesGui.showTypes(listOf(MobsJeiCategory.TYPE))
		}))
		val howto = categories.filterIsInstance<HowToJeiCategory>().first()
		val prices = categories.filterIsInstance<PricesJeiCategory>().first()
		ApexHowToJei.runtime = rt

		var lastItem: String? = null
		fun itemPage(entryId: String?, category: LayouterCategory<ItemRef>) {
			val id = entryId ?: lastItem
			if (id == null) {
				dev.ashtonite365.apex.util.Client.showChat(Component.literal("§eHover an item and press H to see how to get it."))
				return
			}
			lastItem = id
			rt.recipesGui.showRecipes(category, listOf(ItemRef(id)), listOf())
		}
		ViewerTabs.register(ViewerTab("howto", Component.literal("How to get"), 20, { itemPage(it, howto) }))
		ViewerTabs.register(ViewerTab("prices", Component.literal("Bazaar/AH"), 30, { itemPage(it, prices) }))
		ViewerTabs.hook()
	}

	override fun registerGuiHandlers(registration: IGuiHandlerRegistration) {
		registration.addGenericGuiContainerHandler(
			AbstractContainerScreen::class.java,
			object : IGuiContainerHandler<AbstractContainerScreen<*>> {
				override fun getGuiExtraAreas(containerScreen: AbstractContainerScreen<*>): List<Rect2i> =
					HandledScreenPushREIEvent.publish(HandledScreenPushREIEvent(containerScreen)).rectangles
						.map { Rect2i(it.x, it.y, it.width, it.height) }
			})

		registration.addGlobalGuiHandler(object : mezz.jei.api.gui.handlers.IGlobalGuiHandler {
			override fun getGuiExtraAreas(): Collection<Rect2i> {
				val band = ViewerTabs.currentExclusion()

				ViewerGridRows.stripActive = band != null
				if (band == null) return emptyList()
				noteRowLimit(band.y)
				return listOf(Rect2i(band.x, band.y, band.w, band.h))
			}
		})
	}

	override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) {
		JeiHoverSource.runtime = jeiRuntime
		vanillaBridge.recipeManager = jeiRuntime.recipeManager
		vanillaBridge.focusFactory = jeiRuntime.jeiHelpers.focusFactory
		MobEntities.clear()
		registerViewerTabs(jeiRuntime)
		hideNonApexCategories(jeiRuntime)
		if (!RepoManager.shouldLoadREI()) return

		val im = jeiRuntime.ingredientManager
		val vanilla = im.getAllIngredients(VanillaTypes.ITEM_STACK).toList()
		if (vanilla.isNotEmpty()) im.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, vanilla)
	}

	private fun hideNonApexCategories(rt: IJeiRuntime) {
		runCatching {
			val rm = rt.recipeManager
			val hidden = rm.createRecipeCategoryLookup().get().toList()
				.map { it.recipeType }
				.filter { dev.ashtonite365.apex.repo.recipes.JeiCategoryFilter.hide(it.uid.namespace, Apex.MOD_ID) }
			hidden.forEach { rm.hideRecipeCategory(it) }
			Apex.logger.info("[ItemList] hid ${hidden.size} non-SkyBlock recipe categories: ${hidden.joinToString { it.uid.toString() }}")
		}.onFailure { Apex.logger.warn("[ItemList] could not hide the vanilla recipe categories", it) }
	}

	override fun onRuntimeUnavailable() {
		JeiHoverSource.runtime = null
		ViewerGridRows.stripActive = false
		ViewerTabs.host = null
	}
}
