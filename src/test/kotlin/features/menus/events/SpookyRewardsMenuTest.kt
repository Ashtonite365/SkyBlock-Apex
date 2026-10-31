package dev.ashtonite365.apex.test.features.menus.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.events.SpookyRewardsParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SpookyRewardsMenuTest {
	@Test fun parses() {
		val d = RealDumpFixtures.load("spooky-rewards")
		val m = SpookyRewardsParser.parse(d.title, d.slots)!!
		assertEquals(listOf("VIP", "VIP+", "MVP", "MVP+", "MVP++"), m.bundles.map { it.name.removeSuffix(" Rewards Bundle") })

		for (b in m.bundles) assertEquals(b.declaredCount, b.rewards.size, b.name)
		assertEquals("Creeper Spray", m.bundles[0].rewards[0])
		assertEquals("[MVP++]", m.bundles.last().requirement)
		assertEquals(listOf("Hypixel Store" to 32), m.extras)
		assertEquals(mapOf("Close" to 31), m.nav)
		assertNull(SpookyRewardsParser.parse(d.title, d.slots.filter { it.slot > 20 }))
	}
}
