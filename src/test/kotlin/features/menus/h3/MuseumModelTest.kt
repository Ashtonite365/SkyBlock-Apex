// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.h3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.museum.MuseumCategory
import dev.ashtonite365.apex.features.menus.museum.MuseumEntry
import dev.ashtonite365.apex.features.menus.museum.MuseumFilter
import dev.ashtonite365.apex.features.menus.museum.MuseumParser
import dev.ashtonite365.apex.features.menus.museum.MuseumSort
import dev.ashtonite365.apex.features.menus.museum.MuseumStatus
import dev.ashtonite365.apex.features.menus.support.SlotText

class MuseumModelTest {
	private val overview = listOf(
		SlotText(19, "§aWeapons", listOf("§7Items Donated: §e45§6/§e120", "§eClick to view!")),
		SlotText(20, "§aArmor Sets", listOf("Donated: 10 / 40")),
		SlotText(21, "§aRarities", listOf("§7Progress: 3/9")),
		SlotText(22, "§aSpecial Items", listOf("§7Display your special items")),
		SlotText(40, "Museum Rewards", listOf("12/50")),
		SlotText(49, "Close"),
		SlotText(0, "§7 "),
	)
	private val weapons = listOf(
		SlotText(10, "§6Aspect of the End", listOf("§7Damage: +100", "", "§a✔ Donated")),
		SlotText(11, "§cHyperion", listOf("§cNot donated yet", "§eClick to donate!")),
		SlotText(12, "§c???", listOf()),
		SlotText(13, "§aBonemerang", listOf("§7Status: §aDonated")),
		SlotText(14, "Midas' Sword", listOf("§7Locked until you craft it")),
		SlotText(4, "Weapons", listOf("Items Donated: 45/120")),
		SlotText(53, "§aNext Page", listOf("Page 2")),
		SlotText(45, "§aPrevious Page"),
		SlotText(48, "§aGo Back", listOf("To Your Museum")),
		SlotText(1, "Stone", listOf("just a block")),
	)

	@Test
	fun titlesAreTolerant() {
		assertEquals(Triple(null, null, null), MuseumParser.parseTitle("Your Museum"))
		assertEquals(Triple(null, null, null), MuseumParser.parseTitle("§8Steve's Museum"))
		assertEquals(Triple("Weapons", null, null), MuseumParser.parseTitle("Museum ➜ Weapons"))
		assertEquals(Triple("Armor Sets", 2, 3), MuseumParser.parseTitle("Your Museum > Armor Sets (2/3)"))
		assertEquals(Triple("Rarities", 1, 4), MuseumParser.parseTitle("Rarities [1 / 4]"))
		assertEquals(Triple("Armour Sets", null, null), MuseumParser.parseTitle("Armour Sets"))
		for (t in listOf("Bazaar ➜ Farming", "Museums of the world tour", "Weapon Shop", "")) assertNull(MuseumParser.parseTitle(t), t)
		assertTrue(MuseumParser.ANY_TITLE.containsMatchIn("Your Museum"))
		assertTrue(!MuseumParser.ANY_TITLE.containsMatchIn("Bank"))
	}

	@Test
	fun parsesOverview() {
		val m = MuseumParser.parse("Your Museum", overview)!!
		assertTrue(m.isOverview)
		assertEquals(listOf(
			MuseumCategory("Weapons", 19, 45, 120), MuseumCategory("Armor Sets", 20, 10, 40),
			MuseumCategory("Rarities", 21, 3, 9), MuseumCategory("Special Items", 22, null, null),
			MuseumCategory("Museum Rewards", 40, null, null),
		), m.categories)
		assertEquals(0.375, m.categories[0].fraction!!, 1e-9)
		assertNull(m.categories[3].fraction)
	}

	@Test
	fun parsesCategoryPage() {
		val m = MuseumParser.parse("Museum ➜ Weapons (1/2)", weapons)!!
		assertEquals("Weapons", m.category)
		assertEquals(listOf(
			MuseumEntry(10, "Aspect of the End", MuseumStatus.DONATED), MuseumEntry(11, "Hyperion", MuseumStatus.MISSING),
			MuseumEntry(12, "???", MuseumStatus.LOCKED), MuseumEntry(13, "Bonemerang", MuseumStatus.DONATED),
			MuseumEntry(14, "Midas' Sword", MuseumStatus.LOCKED),
		), m.entries)
		assertEquals(53, m.nextSlot); assertEquals(45, m.prevSlot); assertEquals(48, m.backSlot)
		assertEquals(1, m.page); assertEquals(2, m.pages)
		assertEquals(45, m.donatedShown); assertEquals(120, m.totalShown)
	}

	@Test
	fun garbageFallsBackToVanilla() {
		assertNull(MuseumParser.parse("Your Museum", emptyList()))
		assertNull(MuseumParser.parse("Your Museum", listOf(SlotText(1, "Stone"), SlotText(2, "Dirt", listOf("x")))))
		assertNull(MuseumParser.parse("Museum ➜ Weapons", listOf(SlotText(1, "Stone", listOf("A block")), SlotText(53, "Next Page"))))
		assertNull(MuseumParser.parse("Bank", weapons))
		assertEquals(MuseumStatus.UNKNOWN, MuseumParser.status("§kxx", listOf("§r")))
		assertEquals(MuseumStatus.MISSING, MuseumParser.status("Thing", listOf("NOT  DONATED")))
	}

	@Test
	fun sortingAndFiltering() {
		val d1 = MuseumEntry(5, "zeta", MuseumStatus.DONATED); val m1 = MuseumEntry(9, "Alpha", MuseumStatus.MISSING)
		val l1 = MuseumEntry(1, "beta", MuseumStatus.LOCKED); val m2 = MuseumEntry(2, "gamma", MuseumStatus.MISSING)
		val l = listOf(d1, m1, l1, m2)
		assertEquals(listOf(l1, m2, d1, m1), MuseumParser.sort(l, MuseumSort.SLOT))
		assertEquals(listOf(m2, m1, l1, d1), MuseumParser.sort(l, MuseumSort.STATUS))
		assertEquals(listOf(m1, l1, m2, d1), MuseumParser.sort(l, MuseumSort.NAME))
		assertEquals(listOf(d1), MuseumParser.filter(l, MuseumFilter.DONATED))
		assertEquals(listOf(m1, l1, m2), MuseumParser.filter(l, MuseumFilter.MISSING))
		assertEquals(l, MuseumParser.filter(l, MuseumFilter.ALL))
		assertTrue(MuseumParser.sameCategory("Armour  Sets", "§aarmor sets"))
	}

}
