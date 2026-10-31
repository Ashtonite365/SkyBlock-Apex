// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.util.skyblock.DungeonSecretCounts
import dev.ashtonite365.apex.util.skyblock.DungeonSecretCounts.RoomCount

class DungeonSecretCountsTest {
	private fun ab(line: String) = DungeonSecretCounts.fromActionBar(line)

	@Test fun actionBarClassic() {
		assertEquals(RoomCount(3, 7), ab("§c1,234/1,500❤     §a3/7 Secrets     §b893/893✎ Mana"))
		assertEquals(RoomCount(0, 5), ab("§7§e0§7/§e5 Secrets"))
		assertEquals(RoomCount(5, 5), ab("§c2000/2000❤     §75/5 Secrets"))
	}

	@Test fun actionBarPrivateUseGlyphsAndCommas() {
		assertEquals(RoomCount(2, 9), ab("§c12,345/12,345\uE010     §7\uE02A §e2§7/§e9 Secrets     §b1,893/2,000\uE003"))
		assertEquals(RoomCount(1, 4), ab("\uE010 1,000/1,000   1 / 4\uE030 Secrets"))
		assertEquals(RoomCount(4, 10), ab("§a4\u00A0/\u00A010 Secrets"))
	}

	@Test fun actionBarNoMatch() {
		assertNull(ab("§c1600/1500❤     §b893/893✎ §3120ʬ"))
		assertNull(ab("§c1,600/1,500❤     §a1,600/1,500 Secrets?"))
		assertNull(ab("§a8/7 Secrets"))
		assertEquals(RoomCount(0, 0), ab("§c2000/2000❤     §7No Secrets"))
	}

	@Test fun tabListCountAndPercent() {
		val run = DungeonSecretCounts.fromTab(listOf("§b§lDungeon Stats", " §7Secrets Found: §b23", " Crypts: §a5", " §7Secrets Found: §638.5%"))
		assertEquals(23, run.found)
		assertEquals(38.5, run.share!!, 1e-9)
		assertEquals(60, run.estimatedTotal)
		assertEquals(37, DungeonSecretCounts.stillNeeded(run, 1.0))
		assertEquals(0, DungeonSecretCounts.stillNeeded(run, 0.3))
	}

	@Test fun tabListGlyphsAndOrder() {
		val run = DungeonSecretCounts.fromTab(listOf("\uE02A Secrets Found: 1,000%", "Secrets Found: 1,012"))
		assertEquals(1012, run.found)
		assertEquals(1000.0, run.share!!, 1e-9)
		assertNull(DungeonSecretCounts.fromTab(listOf("Secrets Found: 0", "Secrets Found: 0%")).estimatedTotal)
	}

	@Test fun colours() {
		assertEquals("§c", DungeonSecretCounts.colourFor(1, 5))
		assertEquals("§e", DungeonSecretCounts.colourFor(3, 5))
		assertEquals("§a", DungeonSecretCounts.colourFor(4, 5))
		assertEquals(true, RoomCount(5, 5).done)
	}
}
