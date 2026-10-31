package dev.ashtonite365.apex.test.features.menus

import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.ah.AhMenuText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextFitTest {
	private fun mc(s: String): Int = s.replace(Regex("§."), "").sumOf { c ->
		when (c) {
			'i', '!', '.', ',', ':', ';', '\'', '|', '·' -> 2
			'l', '`' -> 3
			't', 'I', '[', ']', ' ', '(', ')', '"', '*' -> 4
			'f', 'k', '<', '>', '{', '}' -> 5
			'@', '~' -> 7
			else -> 6
		}.toInt()
	}

	@Test
	fun createAuctionHintFitsItsBoxAtEveryMenuWidth() {
		for (w in 260..700 step 4) {
			val cw = minOf(w - 36 - 9 * 18, 300)
			if (cw < 60) continue
			val lines = AhMenuText.createHintLines(cw - 16, ::mc)
			assertTrue(lines.size in 1..4, "w=$w lines=$lines")
			lines.forEach { assertTrue(mc(it) <= cw - 16, "w=$w '$it' ${mc(it)} > ${cw - 16}") }

			assertTrue(12 + cw + 8 <= w - 12 - 9 * 18, "w=$w column overlaps the inventory grid")
		}
	}

	@Test
	fun hintKeepsEveryWordWhenThereIsRoom() {
		val lines = AhMenuText.createHintLines(200, ::mc)
		assertEquals("Put an item in: click it in your inventory with the vanilla menu", lines.joinToString(" ") { it.removePrefix("§7") })
	}

	@Test
	fun inventoryHeaderFitsTheGrid() {
		assertTrue(mc(AhMenuText.inventoryHeader(9 * 18, ::mc)) <= 9 * 18)
		assertEquals("§7Your inventory", AhMenuText.inventoryHeader(100, ::mc))
	}

	@Test
	fun wrapFitCapsLinesAndWidth() {
		val l = MenuTheme.wrapFit("one two three four five six seven eight nine ten", 40, 2, ::mc, "§7")
		assertEquals(2, l.size)
		l.forEach { assertTrue(mc(it) <= 40, it) }
		assertTrue(l[1].endsWith("…"))
	}
}
