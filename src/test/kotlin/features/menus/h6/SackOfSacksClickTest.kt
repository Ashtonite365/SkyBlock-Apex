// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.HubClicks
import dev.ashtonite365.apex.features.menus.h6.HubClicks.Kind
import dev.ashtonite365.apex.features.menus.support.ScrollClip
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.ScrollMemory

class SackOfSacksClickTest {
	private fun sack(slot: Int, name: String) = SlotText(slot, name, listOf("Holds various items obtained from", "Combat!", "",
		"Capacity: 20,160 of each item", "Multiple sacks sum their capacity!", "", "Right Click to open!", "", "EPIC SACK"))

	private val slots = listOf(sack(0, "Large Enchanted Combat Sack"), sack(1, "Large Combat Sack"), sack(15, "Small Slayer Sack"),
		SlotText(39, "Go Back", listOf("To Your Bags")), SlotText(41, "Insert inventory", listOf("Inserts your inventory items into", "your sacks.", "", "Click to put items in!")))

	@BeforeEach fun clear() = ScrollMemory.clear()
	@AfterEach fun done() = ScrollMemory.clear()

	@Test fun leftPicksUpRightOpensOnTheSameSlot() {
		val m = BagsParser.parse("Sack of Sacks", slots)!!
		val combat = m.allTiles.single { it.name.contains("Large Combat Sack") }
		assertEquals(1, combat.slot, "tile is bound to the sack's own server slot")
		assertEquals(HubClicks.Route(0, Kind.ITEM_PICKUP), HubClicks.route(combat, 0, shift = false), "left: normal pickup (take out)")
		assertEquals(HubClicks.Route(0, Kind.ITEM_QUICK_MOVE), HubClicks.route(combat, 0, shift = true), "shift-left: to the inventory")
		assertEquals(HubClicks.Route(1, Kind.BUTTON), HubClicks.route(combat, 1, shift = false), "right: opens the sack")
		val slayer = m.allTiles.single { it.name.contains("Small Slayer Sack") }
		assertEquals(15, slayer.slot)
		assertEquals(HubClicks.Route(0, Kind.ITEM_PICKUP), HubClicks.route(slayer, 0, shift = false))
		assertTrue(BagsParser.acceptsItems(m.heading), "the inventory panel is shown so the sack can be placed")
	}

	@Test fun clickedTileWithRestoredScrollMapsToItsOwnSlot() {

		ScrollView { "sacks" }.apply { layout(MenuBounds(0, 0, 200, 100), 400); repeat(2) { scroll(-1.0) } }
		val v = ScrollView { "sacks" }
		v.layout(MenuBounds(0, 0, 200, 100), 400)
		val off = v.offset
		assertTrue(off > 0, "offset restored")

		val rows = (0 until 12).map { r -> r to MenuBounds(0, r * 30 - off, 200, 28) }
		val my = 50
		val hit = rows.filter { ScrollClip.hit(10, my, it.second, v.view) }.map { it.first }
		assertEquals(listOf((my + off) / 30), hit, "the visual position hits the row under the cursor, offset applied once")
	}
}
