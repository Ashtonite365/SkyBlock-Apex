package dev.ashtonite365.apex.test.features.menus.hub

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.hub.HubSelectorMenu
import dev.ashtonite365.apex.features.menus.hub.HubSelectorParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class HubSelectorMenuTest {
	@AfterEach fun cleanup() = MenuRegistry.clearForTests()

	@Test fun parses() {
		val d = RealDumpFixtures.load("hub-selector")
		val m = HubSelectorParser.parse(d.title, d.slots)!!
		val count = d.slots.count { it.cleanName.matches(Regex(".+ #\\d+")) }
		assertEquals(count, m.hubs.size)
		val h1 = m.hubs.first()
		assertEquals(1, h1.number); assertEquals(10, h1.slot); assertEquals(49, h1.players); assertEquals(60, h1.capacity); assertEquals("mega34A", h1.server)
		val full = m.hubs.first { it.server == "mini20CS" }
		assertFalse(full.joinable)
		assertEquals(m.hubs.size - 1, m.open.size)
		assertEquals(mapOf("Close" to 49), m.nav)
	}

	@Test fun classify() {
		val d = RealDumpFixtures.load("hub-selector")
		assertNull(HubSelectorParser.parse(d.title, d.slots.filter { it.slot >= 45 }))
		MenuRegistry.register(HubSelectorMenu)
		assertEquals("hub-selector", MenuRegistry.match("SkyBlock Hub Selector")?.id)
	}
}
