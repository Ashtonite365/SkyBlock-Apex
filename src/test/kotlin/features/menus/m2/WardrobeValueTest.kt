// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.m2

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeParser
import dev.ashtonite365.apex.features.menus.wardrobe.WardrobeValue
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class WardrobeValueTest {
	private val d = RealDumpFixtures.load("wardrobe-p1-1019")
	private val fmt: (Double) -> String = { "%.0f".format(it) }

	@Test fun setPiecesFromTheRealDump() {
		val m = WardrobeParser.parse(d.title, d.slots)!!
		val set1 = m.sets.first { it.index == 0 }
		assertEquals(4, set1.pieces.size, "${set1.pieces}")
		assertTrue(set1.pieces[0].startsWith("Ancient Tarantula Helmet"), set1.pieces[0])
	}

	@Test fun totalIsTheSumAndUnknownSaysNoLivePrice() {
		val all = WardrobeValue.lines(1, listOf(WardrobeValue.Piece("A", 10.0), WardrobeValue.Piece("B", 5.0)), fmt)
		assertEquals("§6Set 1 value: §e15 coins", all[0])
		val part = WardrobeValue.lines(2, listOf(WardrobeValue.Piece("A", 10.0), WardrobeValue.Piece("B", null)), fmt)
		assertTrue(part[0].contains("partial"), part[0]); assertEquals("§7B: §cno live price", part[2])
		assertEquals("§6Set 3 value: §cno live price", WardrobeValue.lines(3, listOf(WardrobeValue.Piece("A", null)), fmt)[0])
	}

	@Test fun wrapsNeverCuts() {
		val long = "§7" + "Ancient Storm's Chestplate ✪✪✪✪✪ of Very Long Names: §e1"
		val out = WardrobeValue.wrap(listOf(long), 60) { it.replace(Regex("§."), "").length * 6 }
		assertTrue(out.size > 1)
		assertEquals(long.replace(Regex("§."), "").split(' ').filter { it.isNotEmpty() }, out.joinToString(" ").replace(Regex("§."), "").split(' ').filter { it.isNotEmpty() })
		assertTrue(out.all { it.startsWith("§7") })
	}
}
