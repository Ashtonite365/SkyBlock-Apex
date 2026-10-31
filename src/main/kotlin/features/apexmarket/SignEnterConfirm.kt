/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (H4): Enter confirms sign. Pressing Enter in a bazaar/AH price or amount sign closes the
 * sign (which submits it, exactly like clicking Done). Only reacts to your own keypress; never automatic.
 * The search sign is already replaced by SignSearchScreen; Apex never fills amount or price signs, so this
 * covers every remaining vanilla sign.
 */
package dev.ashtonite365.apex.features.apexmarket

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import dev.ashtonite365.apex.features.auction.AuctionTools
import dev.ashtonite365.apex.mixins.accessor.AccessorSignEditScreen
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object SignEnterConfirm {
	@Config
	object TConfig : ManagedConfig("sign-enter-confirm", Category.AUCTION_BAZAAR) {
		val enabled by toggle("enabled") { true }
	}

	fun isMarketSign(lines: List<String>, containerTitle: String): Boolean {
		if (lines.getOrNull(1)?.startsWith("^^^") != true) return false
		if (lines.getOrNull(2) == "Enter query") return false
		val t = containerTitle
		return t.startsWith("Bazaar") || t.contains("➜") || t.startsWith("How much do you want to pay") ||
			t.startsWith("At what price are you selling") || t.startsWith("How many do you want") ||
			t.startsWith("Create BIN Auction") || t.startsWith("Create Auction") || t.startsWith("Auction") ||
			t.startsWith("BIN Auction View") || t.contains("Bid")
	}

	fun onKey(screen: AbstractSignEditScreen, key: Int): Boolean {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock) return false
		if (key != InputConstants.KEY_RETURN && key != InputConstants.KEY_NUMPADENTER) return false
		val acc = screen as AccessorSignEditScreen
		val lines = acc.messages_Apex.toList()
		if (!isMarketSign(lines, AuctionTools.lastContainerTitle)) return false
		screen.onClose()
		return true
	}
}
