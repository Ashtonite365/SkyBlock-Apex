package dev.ashtonite365.apex.features.inventory.storageoverlay

import io.github.notenoughupdates.moulconfig.ChromaColour
import java.util.SortedMap
import kotlinx.serialization.serializer
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.Items
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.resources.Identifier
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.util.StringRepresentable
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.ScreenChangeEvent
import dev.ashtonite365.apex.events.SlotClickEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.async.discard
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.KeptStack

object StorageOverlay {
	val identifier: String = "storage-overlay"

	@Config
	object Data : ProfileSpecificDataHolder<StorageData>(serializer(), "storage-data", { StorageData() })

	@Config
	object TConfig : ManagedConfig(identifier, category = Category.INVENTORY) {
		val alwaysReplace by toggle("always-replace") { false }

		val textureStyle: StorageTextureStyle get() = StorageTextureStyle.STANDARD
		val outlineActiveStoragePage: Boolean by toggle("outline-active-page") { false }
		val outlineActiveStoragePageColour: ChromaColour by colour("outline-active-page-colour") { ChromaColour.fromRGB(255, 255, 0, 0, 255) }
		val showInactivePageTooltips: Boolean by toggle("inactive-page-tooltips") { false }
		val debugClicks by toggle("debug-clicks") { false }

		val columns: Int by integer("rows", 1, 10) { 3 }
		val height: Int by integer("height", 80, 3000) { 324 }
		val retainScroll: Boolean by toggle("retain-scroll") { true }
		val scrollSpeed: Int by integer("scroll-speed", 1, 50) { 10 }
		val inverseScroll: Boolean by toggle("inverse-scroll") { false }
		val padding: Int by integer("padding", 1, 20) { 5 }
		val margin: Int by integer("margin", 1, 60) { 20 }
		val itemsBlockScrolling: Boolean by toggle("block-item-scrolling") { true }
		val highlightSearchResults: Boolean by toggle("highlight-search-results") { true }
		val highlightSearchResultsColour: ChromaColour by colour("highlight-search-results-colour") { ChromaColour.fromRGB(0, 176, 0, 0, 255) }

		val openKey by keyBindingWithDefaultUnbound("open-key")
		val openKeyTarget by choice("open-key-target") { dev.ashtonite365.apex.features.storage.StorageApex.OpenTarget.OVERLAY }
		val hoverPreview by toggle("hover-preview") { true }
		val hoverPreviewScale by integer("hover-preview-scale", 50, 200) { 100 }
		val overviewScale by integer("overview-scale", 50, 200) { 100 }
		val overviewHighlightSearch by toggle("overview-highlight-search") { true }

		val searchKey by keyBindingWithDefaultUnbound("search-key")
		val searchSacks by toggle("search-sacks") { true }
		val searchEquipment by toggle("search-equipment") { true }
		val searchGroupResults by toggle("search-group-results") { true }
	}

	enum class StorageTextureStyle(private val folder: String?) : StringRepresentable {
		STANDARD(null),
		;

		fun sprite(name: String): Identifier =
			Identifier.fromNamespaceAndPath("apex", spritePath(name, dev.ashtonite365.apex.features.menus.style.MenuStyle.state.style))

		fun spritePath(name: String, style: dev.ashtonite365.apex.features.menus.style.Style): String =
			"storageoverlay/" + ((folder ?: standardFolder(style))?.let { "$it/" } ?: "") + name

		val followsMenuStyle: Boolean get() = folder == null

		override fun getSerializedName(): String = name

		companion object {

			fun standardFolder(style: dev.ashtonite365.apex.features.menus.style.Style): String? =
				if (style == dev.ashtonite365.apex.features.menus.style.Style.FORGE) "forge" else null
		}
	}

	fun debugLog(message: () -> String) {
		if (TConfig.debugClicks) dev.ashtonite365.apex.Apex.logger.info("[StorageDebug] " + message())
	}

	@Subscribe
	fun tintSearchMatches(event: SlotRenderEvents.Before) {
		val overlay = openOverlay()?.takeIf { TConfig.highlightSearchResults } ?: return
		val query = StorageOverlayScreen.searchText.get().takeUnless { it.isBlank() } ?: return
		val slot = event.slot
		if (overlay.matchesSearch(slot.item ?: return, query))
			event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, TConfig.highlightSearchResultsColour.getEffectiveColourRGB())
	}

	private fun openOverlay(): StorageOverlayScreen? =
		Client.screen as? StorageOverlayScreen ?: (Client.containerScreen?.customGui as? StorageOverlayCustom)?.overview

	fun adjustScrollSpeed(amount: Double): Double =
		amount * (if (TConfig.inverseScroll) TConfig.scrollSpeed else -TConfig.scrollSpeed)

	var overviewToReturnTo: StorageOverviewScreen? = null

	private var keepPageAfterOwnNav = false

	private var openStorageMenu: StorageMenuHandle? = null

	@Subscribe
	fun onChestContentUpdate(event: ChestInventoryUpdateEvent) = snapshotMenu(openStorageMenu)

	@Subscribe
	fun noteTopRowClick(event: SlotClickEvent) {
		val slot = event.slot
		val topRowControl = slot.container !is Inventory && slot.containerSlot < 9 && event.stack.item != Items.STAINED_GLASS_PANE.black
		if (topRowControl && overviewToReturnTo != null) keepPageAfterOwnNav = true
	}

	private fun overlayBeingLeft(old: Screen?): StorageOverlayScreen? =
		old as? StorageOverlayScreen ?: ((old as? AbstractContainerScreen<*>)?.customGui as? StorageOverlayCustom)?.overview

	private fun closeBehindOverview(menu: ChestMenu) {
		val player = Client.player ?: return
		player.connection.send(ServerboundContainerClosePacket(menu.containerId))
		if (player.containerMenu === menu) player.containerMenu = player.inventoryMenu
	}

	@Subscribe
	fun onScreenChange(event: ScreenChangeEvent) {
		val oldScreen = event.old
		val newScreen = event.new
		if (oldScreen == null && newScreen == null) return
		val leavingOverlay = overlayBeingLeft(oldScreen)
		val leavingOverview = oldScreen as? StorageOverviewScreen
		val chest = newScreen as? ContainerScreen

		snapshotMenu(openStorageMenu)
		val previousHandler = openStorageMenu
		val handler = StorageMenuHandle.fromScreen(chest)
		openStorageMenu = handler
		if (leavingOverview != null && previousHandler is StorageMenuHandle.OpenMenu)
			closeBehindOverview(previousHandler.handler)

		if (newScreen == null && leavingOverlay != null && !leavingOverlay.leftOnPurpose) {
			event.overrideScreen = leavingOverlay
			return
		}

		val overview = leavingOverview ?: overviewToReturnTo
		val backToOverview = handler == null || handler is StorageMenuHandle.Overview
		if (overview != null && !overview.isClosing && backToOverview) {
			when {
				keepPageAfterOwnNav -> keepPageAfterOwnNav = false
				else -> { event.overrideScreen = overview; overviewToReturnTo = null }
			}
			return
		}
		if (chest == null || leavingOverlay?.leftOnPurpose == true || handler == null) return
		val overlay = leavingOverlay ?: (if (TConfig.alwaysReplace) StorageOverlayScreen() else null) ?: return
		chest.customGui = StorageOverlayCustom(handler, chest, overlay)
	}

	fun snapshotMenu(handler: StorageMenuHandle?) {
		if (handler == null) return
		val pages = Data.data.knownPages
		if (handler is StorageMenuHandle.Overview) rememberIcons(handler.handler, pages)
		if (handler is StorageMenuHandle.Page) rememberPage(handler.handler, handler.storagePageSlot, pages)
	}

	private fun rememberIcons(menu: ChestMenu, pages: SortedMap<StoragePageSlot, StorageData.RememberedPage>) {
		menu.items.forEachIndexed { menuSlot, item ->

			val page = if (item.isEmpty) null else StoragePageSlot.fromOverviewSlotIndex(menuSlot)
			if (page != null) {
				if (item.item in StorageOverviewScreen.placeholderPanes) {
					pages.remove(page)
				} else {
					val iconRow = listOf(KeptStack.ofStack(item.copy())) + List(8) { KeptStack.empty() }
					pages.getOrPut(page) { StorageData.RememberedPage(page, null) }.icon = VirtualInventory(iconRow)
				}
			}
		}
		Data.markDirty()
	}

	private fun rememberPage(menu: ChestMenu, page: StoragePageSlot, pages: SortedMap<StoragePageSlot, StorageData.RememberedPage>) {

		val content = menu.items.subList(9, menu.rowCount * 9).map { KeptStack.ofStack(it.copy()) }
		val snapshot = VirtualInventory(content)
		val entry = pages[page] ?: StorageData.RememberedPage(page, null).also { pages[page] = it }
		entry.inventory = snapshot
		Data.markDirty(snapshot.serializationCache.discard())
	}
}
