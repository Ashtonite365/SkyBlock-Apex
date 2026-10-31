package dev.ashtonite365.apex.test.features.plus.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.commands.PlusCommands

class PlusCommandsTest {
	@Test
	fun invites() {
		PlusCommands.handleChatLine("[MVP+] STPREAPER has invited you to join their party!")
		assertEquals("STPREAPER", PlusCommands.lastInviter)
		PlusCommands.handleChatLine("The party invite from [VIP] Other has expired.")
		assertEquals("STPREAPER", PlusCommands.lastInviter)
		PlusCommands.handleChatLine("The party invite from [MVP+] STPREAPER has expired.")
		assertNull(PlusCommands.lastInviter)
		PlusCommands.handleChatLine("SkyLime1213 has invited you to join their party! You have 60 seconds to accept. Click here to join!")
		assertEquals("SkyLime1213", PlusCommands.lastInviter)
		assertTrue(PlusCommands.isBareAccept("P Accept"))
		assertFalse(PlusCommands.isBareAccept("p accept Bob"))
	}

	@Test
	fun shortWarp() {
		val warps = setOf("crypt", "jerry", "hub")
		assertEquals("crypt", PlusCommands.shortWarpFor("Crypt", warps, false))
		assertNull(PlusCommands.shortWarpFor("jerry", warps, true))
		assertNull(PlusCommands.shortWarpFor("hub now", warps, false))
		assertNull(PlusCommands.shortWarpFor("pv", warps, false))
	}
}
