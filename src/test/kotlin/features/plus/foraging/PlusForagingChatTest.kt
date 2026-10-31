package dev.ashtonite365.apex.test.features.plus.foraging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.foraging.PlusForagingChat

class PlusForagingChatTest {
	@Test
	fun sweep() {
		assertEquals("34,442.2", PlusForagingChat.parseSweepHeader("Sweep Details: 34,442.2∮ Sweep"))
		assertEquals("Fig" to "18.13", PlusForagingChat.parseToughness("  Fig Tree Toughness: 3.5 18.13 Logs"))
		assertEquals("§6Sweep: §2442∮ §7(Fig, §a18 logs§7)", PlusForagingChat.summary(PlusForagingChat.Sweep("442", "Fig", "18")))
	}
}
