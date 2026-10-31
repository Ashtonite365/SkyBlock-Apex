package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.h6.NpcShopParser
import dev.ashtonite365.apex.features.menus.support.CopperReadout
import dev.ashtonite365.apex.features.menus.support.SlotText

class SkyMartCopperTest {
	private fun page(vararg offers: SlotText) = offers.toList() + SlotText(49, "Sell Item", listOf("Click items in your inventory to", "sell them to this Shop!"))
	private fun offer(slot: Int, name: String, cost: String) = SlotText(slot, name, listOf("§9§lRARE", "", "§7Cost", "§c$cost", "", "§eClick to trade!"))

	private val categories = mapOf(
		"SkyMart ➜ Farming Tools" to page(offer(10, "Rookie Hoe", "25 Copper"), offer(11, "Basic Gardening Hoe", "1,500 Copper")),
		"SkyMart ➜ Vacuums" to page(offer(10, "SkyMart Vacuum", "500 Copper")),
		"SkyMart ➜ Miscellaneous" to page(offer(10, "Copper Dye", "2,000,000 Copper")),
	)

	@Test fun everyCategoryShowsCopperNotCoins() {
		for ((title, slots) in categories) {
			assertTrue(NpcShopParser.isCopperShop(title, slots), title)
			val m = NpcShopParser.parse(title, slots)!!
			for (t in m.allTiles.filter { it.slot != 49 }) {
				val info = t.info.first()
				assertTrue(info.endsWith(" Copper"), "$title: $info")
				assertFalse(info.contains("coins", true), "$title: $info")
			}
		}
		val c = NpcShopParser.cost(categories.values.first()[1].lore)!!
		assertEquals(1500.0, c.copper); assertNull(c.coins); assertTrue(c.items.isEmpty())
		assertEquals("2,000,000 Copper", NpcShopParser.costText(NpcShopParser.cost(listOf("Cost", "2,000,000 Copper"))!!))
	}

	@Test fun coinShopsUnchanged() {
		val slots = page(SlotText(10, "Giant Honey Dipper", listOf("Cost", "500,000 Coins", "", "Click to trade!")))
		assertFalse(NpcShopParser.isCopperShop("Bea", slots))
		assertEquals(500000.0, NpcShopParser.cost(slots[0].lore)!!.coins)
	}

	@Test fun liveCopperBalance() {
		assertEquals(1234.0, CopperReadout.parse(emptyList(), listOf("Purse: 9,999,999", "Copper: 1,234")))
		assertEquals(77.0, CopperReadout.parse(listOf(SlotText(4, "SkyMart", listOf("You have: 77 Copper"))), listOf("Copper: 1,234")))
		assertEquals(5.0, CopperReadout.parse(emptyList(), listOf("Purse: 10"), listOf(" Copper: 5")))
		assertNull(CopperReadout.parse(emptyList(), listOf("Purse: 9,999,999")))
		assertEquals(emptyList<String>(), CopperReadout.lines(null, 100) { it.length })
		assertEquals("§7Copper: §c1,234", CopperReadout.text(1234.0))
		assertTrue(CopperReadout.lines(1234567.0, 6) { it.length }.size > 1)
	}
}
