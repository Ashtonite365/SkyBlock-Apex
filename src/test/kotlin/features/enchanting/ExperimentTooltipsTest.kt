package dev.ashtonite365.apex.test.features.enchanting

import dev.ashtonite365.apex.features.enchanting.ExperimentTooltips
import dev.ashtonite365.apex.features.enchanting.ExperimentTooltips.Game
import dev.ashtonite365.apex.features.inventory.TooltipFit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExperimentTooltipsTest {
	@Test
	fun gamesByTitle() {
		assertEquals(Game.SUPERPAIRS, ExperimentTooltips.gameOf("Superpairs (Supreme)"))
		assertEquals(Game.CHRONOMATRON, ExperimentTooltips.gameOf("Chronomatron (High)"))
		assertEquals(Game.ULTRASEQUENCER, ExperimentTooltips.gameOf("Ultrasequencer (Metaphysical)"))
		assertNull(ExperimentTooltips.gameOf("Superpairs Stakes"))
		assertNull(ExperimentTooltips.gameOf("Experimentation Table"))
	}

	@Test
	fun hiddenCardLosesItsTooltipRevealedKeepsName() {
		assertTrue(ExperimentTooltips.isHidden("light_blue_stained_glass_pane", "Click any button!"))
		assertTrue(ExperimentTooltips.isHidden("player_head", "Click any button!"))
		assertFalse(ExperimentTooltips.isHidden("enchanted_book", "Enchanted Book"))
		assertEquals(emptyList<String>(), ExperimentTooltips.trim(listOf("Click any button!", "x"), hidden = true))
		assertEquals(listOf("Enchanted Book"), ExperimentTooltips.trim(listOf("Enchanted Book", "§7Sharpness VII", "lore"), hidden = false))
	}

	@Test
	fun tooltipNeverCoversTheGrid() {

		val grid = ExperimentTooltips.gridRect(listOf(153, 297), listOf(89, 179))!!
		val w = 80; val h = 10
		for ((mx, my) in listOf(160 to 100, 250 to 150, 300 to 190)) {
			val (x, y) = TooltipFit.position(mx, my, w, h, 480, 270, grid)
			val overlaps = x - 4 < grid[2] && x + w + 4 > grid[0] && y - 4 < grid[3] && y + h + 4 > grid[1]
			assertFalse(overlaps, "mouse ($mx,$my) tooltip at ($x,$y) covers grid ${grid.toList()}")
		}

		val (x, _) = TooltipFit.position(250, 150, 140, 10, 400, 270, grid)
		assertTrue(x + 140 + 4 <= grid[0], "x=$x")
	}

	@Test
	fun avoidExpiresAndOutsideTheGridIsVanilla() {
		val grid = intArrayOf(100, 100, 200, 200)
		assertEquals(TooltipFit.position(50, 50, 40, 10, 480, 270, null), TooltipFit.position(50, 50, 40, 10, 480, 270, grid))
		TooltipFit.avoidFor(grid)
		assertNull(TooltipFit.activeAvoid(System.currentTimeMillis() + 1000))
	}
}
