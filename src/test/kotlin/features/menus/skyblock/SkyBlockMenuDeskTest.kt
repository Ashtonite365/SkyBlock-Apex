// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.skyblock.SbTile
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuLayout
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuParser
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class SkyBlockMenuDeskTest {
	private val m = RealDumpFixtures.load("skyblock-menu").let { SkyBlockMenuParser.parse(it.title, it.slots)!! }

	private fun overlap(a: MenuBounds, c: SkyBlockMenuLayout.Cell) =
		a.x < c.x + c.w && c.x < a.x + a.w && a.y < c.y + c.h && c.y < a.y + a.h

	@Test
	fun deskFitsLeftOfStatsAtNormalScales() {
		for (w in listOf(200, 240, 280, 320, 400, 480, 520)) for (h in listOf(180, 220, 260, 300)) for (lw in listOf(22, 30)) {
			val b = MenuBounds(10, 10, w, h)
			val (desk, cells) = SkyBlockMenuLayout.deskButton(SkyBlockMenuLayout.cells(m, b), b, lw)!!
			val stats = cells.first { it.tile == SbTile.STATS }
			val skills = cells.first { it.tile == SbTile.SKILLS }
			val tag = "w=$w h=$h lw=$lw"
			assertTrue(desk.x >= b.x + SkyBlockMenuLayout.PAD, "inside left $tag")
			assertTrue(desk.x + desk.w <= stats.x, "left of Stats $tag")
			assertTrue(desk.y >= stats.y && desk.y + desk.h <= stats.y + stats.h, "Stats row $tag")
			assertTrue(desk.y + desk.h <= skills.y, "above Skills $tag")
			assertEquals(lw + 10, desk.w, "label not clipped $tag")
			assertTrue(stats.w > 0 && stats.x + stats.w <= b.x + b.w, tag)
			for (c in cells) assertFalse(overlap(desk, c), "overlaps ${c.tile} $tag")
		}
	}

	@Test
	fun cellsUnchangedWhenThereIsRoom() {
		val b = MenuBounds(0, 0, 400, 260)
		val cells = SkyBlockMenuLayout.cells(m, b)
		assertEquals(cells, SkyBlockMenuLayout.deskButton(cells, b, 22)!!.second)
	}

	@Test
	fun enabledOnlyOnGarden() {
		assertTrue(SkyBlockMenuLayout.deskEnabled(true))
		assertFalse(SkyBlockMenuLayout.deskEnabled(false))
	}

	@Test
	fun clickSendsDeskOnceAfterDelay() {
		val sent = mutableListOf<String>(); val pending = mutableListOf<() -> Unit>()
		assertTrue(SkyBlockMenuLayout.deskClick(true, { pending += it }, { sent += it }))
		assertTrue(sent.isEmpty(), "not before the delay")
		pending.forEach { it() }
		assertEquals(listOf("desk"), sent)
	}

	@Test
	fun disabledClickDoesNothing() {
		val sent = mutableListOf<String>(); val pending = mutableListOf<() -> Unit>()
		assertFalse(SkyBlockMenuLayout.deskClick(false, { pending += it }, { sent += it }))
		assertTrue(pending.isEmpty() && sent.isEmpty())
	}
}
