package dev.ashtonite365.apex.test.features.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.SolidMenus
import dev.ashtonite365.apex.features.misc.SolidMenus.PaneKind
import dev.ashtonite365.apex.util.render.SidePanel

class SolidMenusTest {
	private fun filler(kind: PaneKind, name: String, lore: List<String> = listOf(), coloured: Boolean = true) =
		SolidMenus.isFiller(kind, name, lore, coloured)

	@Test
	fun blankPanesAreFiller() {
		assertTrue(filler(PaneKind.NEUTRAL, ""))
		assertTrue(filler(PaneKind.NEUTRAL, " "))
		assertTrue(filler(PaneKind.NEUTRAL, "§7"))
		assertTrue(filler(PaneKind.NEUTRAL, "§r §8 "))
		assertTrue(filler(PaneKind.NEUTRAL, "Filler"))
		assertTrue(filler(PaneKind.NEUTRAL, " ", listOf("", "§7")))
		assertTrue(filler(PaneKind.COLOURED, " "))
	}

	@Test
	fun functionalPanesStay() {
		assertFalse(filler(PaneKind.NONE, " "))
		assertFalse(filler(PaneKind.NEUTRAL, "§aClose"))
		assertFalse(filler(PaneKind.COLOURED, "§aSell Instantly"))
		assertFalse(filler(PaneKind.COLOURED, "Farming Collection", listOf("§7Progress: §e50%", "§eClick to view!")))
		assertFalse(filler(PaneKind.NEUTRAL, " ", listOf("§eClick to toggle")))
		assertFalse(filler(PaneKind.NEUTRAL, " ", listOf("§7Some info")))
		assertFalse(filler(PaneKind.COLOURED, "§aEnabled"))
		assertFalse(filler(PaneKind.NEUTRAL, "▶ Next"))
		assertFalse(filler(PaneKind.COLOURED, " ", coloured = false))
	}

	@Test
	fun denyList() {
		for (t in listOf("Experimentation Table", "Chronomatron (Expert)", "Ultrasequencer (Supreme)", "Superpairs (Metaphysical)",
		                 "Harp - Hymn to the Joy", "Melody", "Personal Bank", "Bank Deposit", "Bank Withdrawal",
		                 "Click in order!", "Correct all the panes!", "Change all to same color!", "Select all the RED items!",
		                 "What starts with: 'A'?", "Click the button on time!", "§0Navigate the maze!", "Fossil Excavator", "Experiment Over"))
			assertFalse(SolidMenus.allowedIn(t), t)
		for (t in listOf("Bazaar ➜ Farming", "SkyBlock Menu", "Museum ➜ Combat", "Auctions Browser", "Your Equipment and Stats", "Trades"))
			assertTrue(SolidMenus.allowedIn(t), t)
		assertFalse(SolidMenus.allowedIn(null))
	}

	@Test
	fun denyListWholeWordsOnly() {

		for (t in listOf("Enchant Item ➜ Sharpness", "Sharp", "Sharpshooter", "Essence Shop ➜ Fossil Essence S",
		                 "Experienced Mining", "Melodys Bakery", "Determinal Shop", "Bankruptcy Notice"))
			assertTrue(SolidMenus.allowedIn(t), t)

		for (t in listOf("Harp - Hymn to the Joy", "Melody ♫", "Personal Bank Account", "Core ➜ Bank Upgrades",
		                 "Fossil Excavator", "Experiment Over", "Experimentation Table", "Terminal"))
			assertFalse(SolidMenus.allowedIn(t), t)
	}

	@Test
	fun abbreviation() {
		assertEquals("+8M%", SidePanel.abbreviateNumbers("+8,000,000%"))
		assertEquals("+7.2M%", SidePanel.abbreviateNumbers("+7199900%"))
		assertEquals("1.2B", SidePanel.abbreviateNumbers("1,200,000,000"))
		assertEquals("§612.3k coins", SidePanel.abbreviateNumbers("§612,345 coins"))
		assertEquals("§a+150k", SidePanel.abbreviateNumbers("§a+150,000"))
		assertEquals("999 x 12", SidePanel.abbreviateNumbers("999 x 12"))
		assertEquals("2.5k", SidePanel.abbreviateNumbers("2500"))
		assertEquals("item_1234", SidePanel.abbreviateNumbers("item_1234"))
		assertEquals("123M", SidePanel.abbreviate(123_456_789.0))
	}

	@Test
	fun lightThemeTextIsDarkened() {
		assertEquals("§6Cheapest §8(3)", SidePanel.adapt("§eCheapest §7(3)", SidePanel.LIGHT))
		assertEquals("§eCheapest", SidePanel.adapt("§eCheapest", SidePanel.DARK))
	}
}
