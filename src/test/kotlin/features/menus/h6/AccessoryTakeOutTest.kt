// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.HubClicks
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.support.SlotText

class AccessoryTakeOutTest {
	@Test
	fun bagContentsAreRealItems() {
		assertTrue(BagsParser.contentIsRealItem("Accessory Bag"))
		assertTrue(BagsParser.contentIsRealItem("Accessory Bag (2/3)"))
		assertTrue(BagsParser.contentIsRealItem("Quiver"))
		assertTrue(BagsParser.contentIsRealItem("Potion Bag"))
		assertFalse(BagsParser.contentIsRealItem("Sack of Sacks"))
		assertFalse(BagsParser.contentIsRealItem("Your Bags"))
	}

	@Test
	fun routing() {
		val acc = HubTile(10, "Talisman", realItem = true)
		assertEquals(HubClicks.Route(0, HubClicks.Kind.ITEM_PICKUP), HubClicks.route(acc, 0, shift = false))
		assertEquals(HubClicks.Route(0, HubClicks.Kind.ITEM_QUICK_MOVE), HubClicks.route(acc, 0, shift = true))
		assertEquals(HubClicks.Route(1, HubClicks.Kind.ITEM_PICKUP), HubClicks.route(acc, 1, shift = false))

		val btn = HubTile(20, "Sack", button = 1)
		assertEquals(HubClicks.Route(1, HubClicks.Kind.BUTTON), HubClicks.route(btn, 0, false))
		assertEquals(HubClicks.Route(1, HubClicks.Kind.BUTTON), HubClicks.route(HubTile(5, "x", shiftButton = 1), 0, true))
	}

	@Test
	fun parsedAccessoryTilesAreReal() {
		val slots = listOf(SlotText(10, "Speed Talisman", listOf("§9RARE ACCESSORY")), SlotText(49, "Close"))
		val m = BagsParser.parse("Accessory Bag", slots) ?: return
		m.sections.flatMap { it.tiles }.forEach { assertTrue(it.realItem, "${it.name} must be a real item") }
	}
}
