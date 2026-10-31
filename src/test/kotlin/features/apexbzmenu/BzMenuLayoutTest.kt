package dev.ashtonite365.apex.test.features.apexbzmenu

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout.ColumnSpec

class BzMenuLayoutTest {
	private val specs = listOf(
		ColumnSpec("name", 40, 3.0, 0), ColumnSpec("buy", 30, 1.0, 1), ColumnSpec("sell", 30, 1.0, 2),
		ColumnSpec("vol", 28, 1.0, 3), ColumnSpec("spread", 26, 1.0, 4),
	)

	@Test
	fun wideAreaKeepsAllProportionally() {
		val c = BzMenuLayout.layoutColumns(300, specs, 2)
		Assertions.assertEquals(listOf("name", "buy", "sell", "vol", "spread"), c.map { it.key })

		Assertions.assertEquals(300, c.last().x + c.last().width)
		c.zipWithNext().forEach { (a, b) -> Assertions.assertEquals(a.x + a.width + 2, b.x) }
		c.forEach { col -> Assertions.assertTrue(col.width >= specs.first { it.key == col.key }.minWidth) }
		Assertions.assertTrue(c[0].width > c[1].width)
	}

	@Test
	fun narrowAreaDropsHighestDropOrderFirst() {

		val c = BzMenuLayout.layoutColumns(150, specs, 2)
		Assertions.assertEquals(listOf("name", "buy", "sell", "vol"), c.map { it.key })
		Assertions.assertTrue(c.last().x + c.last().width <= 150)
		Assertions.assertEquals(listOf("name"), BzMenuLayout.layoutColumns(50, specs, 2).map { it.key })
	}

	@Test
	fun neverDropColumnIsSqueezedNotLost() {
		val c = BzMenuLayout.layoutColumns(20, specs, 2)
		Assertions.assertEquals(listOf("name"), c.map { it.key })
		Assertions.assertEquals(20, c[0].width)
		Assertions.assertEquals(listOf<BzMenuLayout.Column>(), BzMenuLayout.layoutColumns(100, listOf(), 2))
	}

	private val mono: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }

	@Test
	fun wrapTextBreaksOnWordsAndCarriesColour() {
		val lines = BzMenuLayout.wrapText("§cWide spread: 93.5% (max 15%)", 60, mono)
		Assertions.assertTrue(lines.size > 1)
		lines.forEach { Assertions.assertTrue(mono(it) <= 60, it) }
		Assertions.assertTrue(lines.drop(1).all { it.startsWith("§c") })
		Assertions.assertEquals("Wide spread: 93.5% (max 15%)", lines.joinToString(" ").replace("§c", ""))
	}

	@Test
	fun wrapTextHardBreaksLongWords() {
		val lines = BzMenuLayout.wrapText("ABCDEFGHIJ", 24, mono)
		Assertions.assertEquals(listOf("ABCD", "EFGH", "IJ"), lines)
		Assertions.assertEquals(listOf("short"), BzMenuLayout.wrapText("short", 100, mono))
	}

	@Test
	fun detailFallsBackToSelectionWhenNothingValidIsHovered() {
		val known: (String) -> Boolean = { it in setOf("MITHRIL_ORE", "WHEAT") }
		Assertions.assertEquals("MITHRIL_ORE", BzMenuLayout.detailProductId(null, "MITHRIL_ORE", known))
		Assertions.assertEquals("MITHRIL_ORE", BzMenuLayout.detailProductId("", "MITHRIL_ORE", known))
		Assertions.assertEquals("MITHRIL_ORE", BzMenuLayout.detailProductId("NOT_A_PRODUCT", "MITHRIL_ORE", known))
		Assertions.assertEquals("WHEAT", BzMenuLayout.detailProductId("WHEAT", "MITHRIL_ORE", known))
		Assertions.assertNull(BzMenuLayout.detailProductId(null, null, known))
		Assertions.assertNull(BzMenuLayout.detailProductId(null, "GONE", known))
	}

	@org.junit.jupiter.api.Test
	fun wrapTabsWrapsIntoRows() {
		val t = BzMenuLayout.wrapTabs(listOf(40, 40, 40, 200), 100)
		org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 0, 1, 2), t.map { it.row })
		org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 42, 0, 0), t.map { it.x })
		org.junit.jupiter.api.Assertions.assertEquals(100, t[3].width)
	}

	@org.junit.jupiter.api.Test
	fun wrapTabsPacksTightlyWithSmallGap() {
		val t = BzMenuLayout.wrapTabs(listOf(12, 30, 30, 30, 12), 100, gap = 1)
		org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 0, 0, 1, 1), t.map { it.row })
		org.junit.jupiter.api.Assertions.assertEquals(listOf(0, 13, 44, 0, 31), t.map { it.x })
	}
}
