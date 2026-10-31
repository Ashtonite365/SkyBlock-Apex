// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.recipes.itemlist

import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.items.recipes.ViewerTab
import dev.ashtonite365.apex.features.items.recipes.ViewerTabs
import dev.ashtonite365.apex.util.Client

interface ItemListNavigator {

	fun leaveRecipeScreen()

	fun openMob(mobId: String?)

	fun openItemPage(page: ItemListPage<ItemPageRef>, itemId: String)

	fun openRecipesFor(itemId: String)
}

object ItemListNavigation {
	@Volatile
	var navigator: ItemListNavigator? = null

	fun openRecipesFor(itemId: String) {
		navigator?.openRecipesFor(itemId)
	}

	fun registerTabs(nav: ItemListNavigator, showChat: (Component) -> Unit = { Client.showChat(it) }) {
		navigator = nav
		var lastItem: String? = null
		fun itemPage(entryId: String?, page: ItemListPage<ItemPageRef>) {
			val id = entryId ?: lastItem
			if (id == null) {
				showChat(Component.literal("§eHover an item and press H to see how to get it."))
				return
			}
			lastItem = id
			nav.openItemPage(page, id)
		}
		ViewerTabs.register(ViewerTab("items", Component.literal("Items"), 0, { _ -> nav.leaveRecipeScreen() }))
		ViewerTabs.register(ViewerTab("mobs", Component.literal("Mobs"), 10, { nav.openMob(it?.takeIf { id -> MobPageRef(id).mob != null }) }))
		ViewerTabs.register(ViewerTab("howto", Component.literal("How to get"), 20, { itemPage(it, HowToPage) }))
		ViewerTabs.register(ViewerTab("prices", Component.literal("Bazaar/AH"), 30, { itemPage(it, PricesPage) }))
	}

	fun release(nav: ItemListNavigator) {
		if (navigator === nav) navigator = null
	}
}
