package dev.ashtonite365.apex.test.features.enchanting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.enchanting.SeqItem
import dev.ashtonite365.apex.features.enchanting.UltrasequencerOrder

class UltrasequencerOrderTest {
	private val pane = SeqItem("black_stained_glass_pane", " ", 1)

	private fun board(vararg numbered: Pair<Int, SeqItem>): List<SeqItem?> =
		MutableList(54) { i -> numbered.firstOrNull { it.first == i }?.second ?: pane }.also {
			it[49] = SeqItem("glowstone", "Remember the pattern!", 1)
		}

	@Test
	fun allSixNumbersIncludingLegacyDyeColours() {
		val slots = board(
			10 to SeqItem("ink_sac", "1", 1),
			12 to SeqItem("lime_dye", "2", 2),
			14 to SeqItem("cocoa_beans", "3", 3),
			16 to SeqItem("red_dye", "4", 4),
			28 to SeqItem("yellow_dye", "5", 5),
			30 to SeqItem("light_blue_dye", "6", 6),
		)
		assertEquals(mapOf(0 to 10, 1 to 12, 2 to 14, 3 to 16, 4 to 28, 5 to 30), UltrasequencerOrder.read(slots))
	}

	@Test
	fun lapisAndBoneMealAreNumbersToo() {
		assertEquals(6, UltrasequencerOrder.orderIndex(SeqItem("lapis_lazuli", "7", 7)))
		assertEquals(7, UltrasequencerOrder.orderIndex(SeqItem("bone_meal", "8", 8)))
	}

	@Test
	fun nameWinsOverCount() {

		assertEquals(9, UltrasequencerOrder.orderIndex(SeqItem("orange_dye", "10", 1)))
	}

	@Test
	fun nonNumberedSlotsIgnored() {
		assertNull(UltrasequencerOrder.orderIndex(pane))
		assertNull(UltrasequencerOrder.orderIndex(SeqItem("glowstone", "Remember the pattern!", 1)))
		assertNull(UltrasequencerOrder.orderIndex(SeqItem("clock", "Timer: 10s", 1)))
		assertNull(UltrasequencerOrder.orderIndex(SeqItem("air", "Air", 0)))
	}
}
