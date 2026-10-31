package dev.ashtonite365.apex.features.inventory

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.ItemCache.isBroken
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.stackUnderCursor
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.keepable
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SBItemUtil.getSearchName

object ItemHotkeys {
	@Config
	object TConfig : ManagedConfig("item-hotkeys", Category.INVENTORY) {
		val lookUpKey by keyBindingWithDefaultUnbound("global-trade-interface")
	}

	fun lookUpCommand(name: String, onBazaar: Boolean, onAuctionHouse: Boolean): String? = when {
		onBazaar -> "bz $name"
		onAuctionHouse -> "ahs $name"
		else -> null
	}

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	@Subscribe
	fun lookUpHovered(event: HandledScreenKeyPressedEvent) {
		if (!event.matches(TConfig.lookUpKey)) return
		val hovered = event.screen.stackUnderCursor?.keepable() ?: return
		val id = hovered.repoItemId ?: return

		val named = RepoManager.getCoreItem(id)?.asItemStack()?.takeUnless { it.isBroken } ?: hovered
		val command = lookUpCommand(named.getSearchName(), HypixelStaticData.hasBazaarStock(id.bazaarProduct), HypixelStaticData.hasAuctionHouseOffers(id)) ?: return
		Client.runCommand(command)
		event.cancel()
	}
}
