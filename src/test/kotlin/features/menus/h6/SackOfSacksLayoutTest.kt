package dev.ashtonite365.apex.test.features.menus.h6

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.menus.h6.BagsParser
import dev.ashtonite365.apex.features.menus.h6.HubTileGrid
import dev.ashtonite365.apex.features.menus.h6.HubTileText

class SackOfSacksLayoutTest {
	private val pad = 6; private val gap = 4

	@Test fun fourPerRowAndTextFits() {

		for (w in listOf(320, 360, 420, 480)) {
			val tileW = HubTileGrid.tileWidth(w - 2 * pad, gap, BagsParser.SACK_COLS)
			assertTrue(4 * tileW + 3 * gap <= w - 2 * pad, "w=$w: 4 tiles fit")
			val textW = tileW - 26
			val s = BagsParser.SACK_TEXT_SCALE
			val measure = { t: String -> kotlin.math.ceil(t.replace(Regex("§."), "").length * 6 * s).toInt() }
			for (name in listOf("Enchanted Agronomy Sack", "Large Dungeon Sack", "Large Enchanted Husbandry Sack")) {
				val lines = BzMenuLayout.wrapText(name, textW, measure)
				assertEquals(name, lines.joinToString(" ").replace(Regex("§."), "").replace(Regex("\\s+"), " ").trim(), "w=$w: never truncated")
				assertTrue(lines.all { measure(it) <= textW }, "w=$w: $lines fit $textW")
			}
		}
		assertTrue(BagsParser.SACK_TEXT_SCALE in 0.85f..0.9f)
		assertEquals(8, HubTileGrid.lineH(0.88f))
		assertEquals(9 + 5 + 4 + 3 + 9, HubTileText.height(1, 1))
	}
}
