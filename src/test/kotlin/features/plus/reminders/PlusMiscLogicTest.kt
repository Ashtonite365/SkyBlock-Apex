package dev.ashtonite365.apex.test.features.plus.reminders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.reminders.PlusMiscLogic

class PlusMiscLogicTest {
	@Test
	fun parsing() {
		assertTrue(PlusMiscLogic.isKick("You were kicked while joining that server!"))
		assertEquals(201, PlusMiscLogic.restartSeconds(listOf("Server closing: 03:21")))
		assertEquals("Late Spring 7th 5:20am ☀", PlusMiscLogic.dateLine(listOf(" Late Spring 7th", " 5:20am ☀")))
		assertEquals(20.0, PlusMiscLogic.speed(0.6, 0.8), 1e-9)
		assertEquals(listOf("m2", "m1"), PlusMiscLogic.recentServers(mapOf("m1" to 900L, "m2" to 950L, "old" to 0L), 1000L, 200L))
	}
}
