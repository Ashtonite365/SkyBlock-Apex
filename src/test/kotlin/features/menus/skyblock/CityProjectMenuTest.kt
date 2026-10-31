// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.skyblock.CityProjectMenu
import dev.ashtonite365.apex.features.menus.skyblock.CityProjectParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class CityProjectMenuTest {
	private val d = RealDumpFixtures.load("city-project")
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test
	fun realDump() {
		val m = CityProjectParser.parse(d.title, d.slots)!!
		assertEquals("Hub Revamp", m.name)
		assertEquals("100%", m.progress); assertEquals("RELEASED!", m.stage); assertEquals("300,000", m.total)
		assertEquals(listOf(19, 21, 23, 25), m.components.map { it.slot })
		assertEquals(listOf(28, 30, 32, 34), m.components.map { it.contributeSlot })
		assertEquals("93,842 contributions", m.components[0].global)
		assertEquals(listOf("Enchanted Oak Log x8", "Enchanted Cobblestone x8"), m.components[0].cost)
		assertEquals("Project is released!", m.components[0].status)
		assertEquals(listOf("Enchanted Iron Block", "Magical Water Bucket"), m.components[3].cost, "no xN = 1, shown as-is")
		assertEquals(listOf("Enchanted Acacia Log x16"), m.components[1].cost)
		assertEquals(50 to "0", m.bonus)
		assertEquals(mapOf("Go Back" to 48, "Close" to 49), m.nav)
	}

	@Test
	fun titleClaimsOnlyRealProjectChests() {
		MenuRegistry.register(object : CustomMenu by CityProjectMenu { override val enabled = true })
		for (t in listOf("Previous City Projects", "§aPrevious City Projects", "City Projects", "Hub Revamp", "Revamp", "Project Previous - X", "Community Shop"))
			assertTrue(MenuRegistry.resolve(t) { error("no view for $t") } is MenuRegistry.Resolution.None, t)
		for (t in listOf("Project - Hub Revamp", "§6Project - Hub Revamp", "Project - Something New"))
			assertTrue(MenuRegistry.resolve(t) { null } is MenuRegistry.Resolution.Fallback, t)
		assertEquals("city-project", MenuRegistry.match("Project - Hub Revamp")?.id)
	}

	@Test
	fun contentsNotTitleDecide() {

		assertEquals(4, CityProjectParser.parse("Project - Something New", d.slots)!!.components.size)
		val shop = RealDumpFixtures.load("community-shop")
		assertNull(CityProjectParser.parse(shop.title, shop.slots), "Community Shop is not the project screen")
		val prev = d.slots.filter { !it.lore.firstOrNull().orEmpty().startsWith("Project Component") }
		assertNull(CityProjectParser.parse("Previous City Projects", prev))
		assertNull(CityProjectParser.parse("Previous City Projects", d.slots), "never claimed, even with project contents")
		MenuRegistry.register(object : CustomMenu by CityProjectMenu { override val enabled = true })
		assertTrue(MenuRegistry.resolve(d.title) { null } is MenuRegistry.Resolution.Fallback)
		assertTrue(MenuRegistry.resolve("Community Shop") { null } is MenuRegistry.Resolution.None)
	}
}
