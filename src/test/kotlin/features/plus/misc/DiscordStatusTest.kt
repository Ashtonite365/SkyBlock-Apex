// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.misc

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.misc.DiscordStatus

class DiscordStatusTest {
	@Test
	fun frameAndPaths() {
		val f = DiscordStatus.frame(1, "{}")
		val b = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN)
		assertEquals(1, b.int); assertEquals(2, b.int); assertEquals(10, f.size)
		val paths = DiscordStatus.candidatePaths({ if (it == "XDG_RUNTIME_DIR") "/run/user/1000" else null }, false)
		assertEquals("/run/user/1000/discord-ipc-0", paths.first())
		assertTrue(paths.all { it.contains("discord-ipc-") && !it.contains("://") })
		assertEquals("\\\\.\\pipe\\discord-ipc-0", DiscordStatus.candidatePaths({ null }, true).first())
		val json = DiscordStatus.activityJson(5, "Hypixel SkyBlock", null, null, "n").toString()
		assertTrue(json.contains("\"SET_ACTIVITY\"") && json.contains("\"details\":\"Hypixel SkyBlock\"") && !json.contains("state"))
	}
}

class MiscPlusPortsTest {
	@Test
	fun piggyAndBottles() {
		assertTrue(dev.ashtonite365.apex.features.plus.misc.MiscPlusPorts.isPiggyBreak("You died, lost 50,000 coins and your piggy bank broke!"))
		assertTrue(dev.ashtonite365.apex.features.plus.misc.MiscPlusPorts.isPiggyBreak("You died and your piggy bank cracked!"))
		assertEquals("You are currently fishing, but Storm in a Bottle is full",
			dev.ashtonite365.apex.features.plus.misc.MiscPlusPorts.chargeBottleMessage(listOf("STORM_IN_A_BOTTLE", "ROD")))
		assertEquals(null, dev.ashtonite365.apex.features.plus.misc.MiscPlusPorts.chargeBottleMessage(listOf("STORM_IN_A_BOTTLE", "THUNDER_IN_A_BOTTLE_EMPTY")))
	}
}
