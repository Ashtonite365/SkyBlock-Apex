// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.exptable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.exptable.ExpTableLayout
import dev.ashtonite365.apex.features.menus.exptable.ExpTableLayout.Kind
import dev.ashtonite365.apex.features.menus.exptable.ExpTableMenu
import dev.ashtonite365.apex.features.menus.exptable.ExpTableParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class ExpTableLayoutTest {
	private val d = RealDumpFixtures.load("exptable-main")
	private val m get() = ExpTableParser.parse(d.title, d.slots)!!

	private fun mc(s: String): Int = s.replace(Regex("§."), "").sumOf { c ->
		when (c) {
			'i', '!', '.', ',', ':', ';', '\'', '|', '·' -> 2
			'l', '`' -> 3
			't', 'I', '[', ']', ' ', '(', ')', '"', '*' -> 4
			'f', 'k', '<', '>', '{', '}' -> 5
			'@', '~' -> 7
			else -> 6
		}
	}
	private fun MenuBounds.inside(o: MenuBounds) = x >= o.x && y >= o.y && x + w <= o.x + o.w && y + h <= o.y + o.h
	private fun MenuBounds.overlaps(o: MenuBounds) = x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h

	private fun bounds(sw: Int, sh: Int) = MenuBounds.centred(sw, sh, ExpTableMenu.defaultSize.first, ExpTableMenu.defaultSize.second)

	@Test fun panelsAtGui3And4() {
		for ((sw, sh) in listOf(480 to 270, 640 to 360)) {
			val b = bounds(sw, sh)
			assertTrue(b.x >= 0 && b.y >= 0 && b.x + b.w <= sw && b.y + b.h <= sh, "fits $sw x $sh")
			val ps = ExpTableLayout.panels(m, b)!!
			fun one(slot: Int) = ps.single { p -> p.tiles.any { it.slot == slot } }
			val chrono = one(29); val sp = one(22); val ultra = one(33)

			assertTrue(chrono.y == sp.y && sp.y == ultra.y && chrono.x < sp.x && sp.x < ultra.x)
			assertTrue(chrono.w > sp.w && ultra.w == chrono.w && sp.w >= 100, "widths ${chrono.w} ${sp.w}")

			val left = one(20); val right = one(23)
			assertEquals(left, one(21)); assertEquals(right, one(24))
			assertEquals(Kind.SIDE, left.kind); assertEquals(Kind.SIDE, right.kind)
			assertEquals(chrono.x, left.x); assertEquals(chrono.w, left.w); assertTrue(left.y > chrono.y + chrono.h - 1)
			assertEquals(ultra.x, right.x); assertEquals(ultra.w, right.w); assertTrue(right.y > ultra.y + ultra.h - 1)
			assertTrue(left.text.any { "Bonus: +1 click" in it }, "${left.text}")
			assertTrue(right.text.any { "Bonus: +2 clicks" in it }, "${right.text}")

			val bottom = listOf(48, 49, 50).map(::one)
			assertTrue(bottom.all { it.kind == Kind.BOTTOM && it.y + it.h == b.y + b.h - ExpTableLayout.PAD && it.y == bottom[0].y })
			assertEquals(bottom.sortedBy { it.x }, bottom)
			assertEquals(m.tiles.size, ps.sumOf { it.tiles.size }, "every item drawn once")
			for (p in ps) {
				assertTrue(p.bounds.inside(b), "${p.text} inside")
				assertTrue(p.y >= b.y + ExpTableLayout.HEADER)
				for (q in ps) if (p !== q) assertFalse(p.bounds.overlaps(q.bounds), "${p.text} overlaps ${q.text}")

				val f = ExpTableLayout.fit(p, ::mc)
				val box = ExpTableLayout.textBox(p)
				assertEquals(1f, f.scale, "${p.text} at full size on $sw x $sh: ${f.lines}")
				assertTrue(f.lines.all { mc(it) <= box.w } && f.height() <= box.h, "${p.text} fits")
				assertTrue(f.lines.none { it.endsWith("…") || it.endsWith("...") })
				assertEquals(p.text.joinToString(" ") { it.replace(Regex("§."), "") }, f.lines.joinToString(" ") { it.replace(Regex("§."), "") }, "all words kept")
			}
		}
	}

	@Test fun missingGameKeepsTheGrid() {
		assertNull(ExpTableLayout.panels(ExpTableParser.parse(d.title, d.slots.filter { it.slot != 33 })!!, bounds(480, 270)))
	}

	@Test fun wrapKeepsColourPerLine() {
		assertEquals(listOf("§aBonus: +2", "§aclicks"), ExpTableLayout.wrap("§aBonus: +2 clicks", 60, ::mc))
	}
}
