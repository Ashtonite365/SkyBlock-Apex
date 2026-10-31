/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Museum Inventory Access (Ashton, 17 Oct 2026).
 *
 * Cause (client side, our own code): the Apex Museum custom menus ("Your Museum", "Museum ➜ <Category>" pages,
 * Museum Milestones) are drawn by MenuFront, which moves EVERY real slot of the chest off screen and only puts the
 * 36 player-inventory slots back when the view draws the shared inventory panel (ApexViewBase.inventoryPanel /
 * InventoryPanel). MuseumView never drew it, so inside the museum menus your inventory was invisible and could not be
 * clicked (Hypixel donates by clicking the item in your inventory). Hypixel does not swap or hide the inventory:
 * vanilla's player inventory (E) is opened client-side and nothing in Apex,, JEI, Mouse Tweaks or Sodium
 * blocks it in the museum.
 *
 * Fix: with this toggle on, every Apex Museum menu reserves room at its bottom and draws the shared live inventory
 * panel there (3 main rows + the hotbar row, Apex panel style). The cells are the REAL player-inventory slots, so
 * items, counts and full tooltips are vanilla's own and every press is one normal vanilla click on that slot (no
 * automation, no packets of our own). It replaces the Milestones page's old "Inventory grid" panel option
 * (custom-menu-museum-milestones.panel-inventory), merged here via settings/duplicates.tsv.
 *
 * Failures are logged with [ApexMuseumInv] and swallowed: the menu stays open on the same page (never closed, never
 * navigated, no vanilla fallback because of the panel).
 */
package dev.ashtonite365.apex.features.museum

import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object MuseumInventoryAccess {
	const val LOG_TAG = "[ApexMuseumInv]"

	@Config
	object TConfig : ManagedConfig("museum-inventory", Category.RENDER) {

		val enabled by toggle("enabled") { true }
	}

	val enabled: Boolean get() = TConfig.enabled

	fun shows(enabled: Boolean, windowSlots: Int): Boolean = enabled && windowSlots >= 36

	fun menuHeight(base: Int, shows: Boolean): Int = base + if (shows) InventoryPanel.RESERVED else 0

	fun contentBottom(viewY: Int, viewH: Int, pad: Int, shows: Boolean): Int =
		viewY + viewH - pad - if (shows) InventoryPanel.RESERVED else 0

	fun panelOrigin(viewX: Int, viewY: Int, viewW: Int, viewH: Int, pad: Int): Pair<Int, Int> =
		viewX + (viewW - InventoryPanel.WIDTH) / 2 to viewY + viewH - pad - InventoryPanel.HEIGHT

	private val logged = mutableSetOf<String>()

	fun guard(where: String, log: (String, Throwable) -> Unit = { m, e -> Apex.logger.warn(m, e) }, block: () -> Unit): Boolean =
		try {
			block(); true
		} catch (e: Throwable) {
			val msg = "$LOG_TAG $where failed: ${e.javaClass.simpleName}: ${e.message}"
			if (logged.add(msg)) log(msg, e)
			false
		}
}
