// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.PurseReadout
import dev.ashtonite365.apex.util.ScoreboardUtil

class PurseReadoutTest {
	@Test fun parsesPurseAndPiggyLines() {
		assertEquals(PurseReadout.Purse("Purse", 1_234_567.0), PurseReadout.parse(listOf("§7Bank", "§fPurse: §61,234,567 §e(+50)")))
		assertEquals(PurseReadout.Purse("Piggy", 12_345.5), PurseReadout.parse(listOf("Piggy: 12,345.5")))
		assertEquals("§7Purse: §61,234,567 coins", PurseReadout.text(PurseReadout.Purse("Purse", 1_234_567.0)))
		assertEquals("§7Piggy: §612,345.5 coins", PurseReadout.text(PurseReadout.Purse("Piggy", 12_345.5)))
	}

	@Test fun unknownMeansNoReadout() {
		assertNull(PurseReadout.parse(listOf("Bits: 100", "Copper: 5")))
		assertNull(PurseReadout.parse(emptyList()))
		assertTrue(PurseReadout.lines(null, 200) { it.length * 6 }.isEmpty())
	}

	@Test fun readsLiveEveryTimeNeverCached() {
		val saved = ScoreboardUtil.simplifiedScoreboardLines
		try {
			ScoreboardUtil.simplifiedScoreboardLines = listOf("Purse: 100")
			assertEquals(100.0, PurseReadout.live()?.coins)
			ScoreboardUtil.simplifiedScoreboardLines = listOf("Piggy: 2,500")
			assertEquals(PurseReadout.Purse("Piggy", 2_500.0), PurseReadout.live())
			ScoreboardUtil.simplifiedScoreboardLines = listOf("Bits: 3")
			assertNull(PurseReadout.live())
		} finally { ScoreboardUtil.simplifiedScoreboardLines = saved }
	}

	@Test fun wrapsNeverTruncates() {
		val width: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }
		val l = PurseReadout.lines(PurseReadout.Purse("Purse", 1_234_567_890.0), 100, width)
		assertTrue(l.size > 1, "$l")
		assertTrue(l.none { it.contains("…") }, "$l")
		assertEquals("Purse: 1,234,567,890 coins", l.joinToString(" ") { it.replace(Regex("§."), "").trim() })
	}
}
