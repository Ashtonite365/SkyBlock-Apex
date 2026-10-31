package dev.ashtonite365.apex.test.features.plus.fishing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureSettings
import dev.ashtonite365.apex.features.plus.fishing.PlusTrophyFillet

class PlusSkipRowsFishingTest {
	private val table = mapOf("BLOBFISH" to listOf(4, 8, 12, 16), "LAVA_HORSE" to listOf(12, 16, 20, 40))

	@Test
	fun fillet() {
		assertEquals(12, PlusTrophyFillet.filletValue("BLOBFISH_GOLD", table))
		assertEquals(40, PlusTrophyFillet.filletValue("LAVA_HORSE_DIAMOND", table))
		assertNull(PlusTrophyFillet.filletValue("BLOBFISH", table))
		assertNull(PlusTrophyFillet.filletValue("MAGMA_FISH_GOLD", table))
	}

	@Test
	fun creatures() {
		assertEquals(setOf("lord jawbus", "thunder"), PlusSeaCreatureSettings.names(" Lord Jawbus, Thunder,, "))
		assertEquals("Thunder", PlusSeaCreatureSettings.creatureOf("You hear a massive rumble as Thunder emerges."))
		assertNull(PlusSeaCreatureSettings.creatureOf("nope"))
	}
}
