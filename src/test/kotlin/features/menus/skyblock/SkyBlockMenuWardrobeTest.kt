// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.skyblock

import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.skyblock.SbTile
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuLayout
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuParser
import dev.ashtonite365.apex.features.menus.skyblock.SkyBlockMenuView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@ExtendWith(AutoBootstrapExtension::class)
class SkyBlockMenuWardrobeTest {
	private val d = RealDumpFixtures.load("skyblock-menu")
	private val m = SkyBlockMenuParser.parse(d.title, d.slots)!!
	private val chipsLore = listOf("Manage your Garden Chips.", "", "Also accessible via /chips", "", "Click to view!")
	private val garden = d.slots.filter { it.slot != 10 && it.slot != 11 } +
		SlotText(10, "§aDesk", listOf("Click to open!")) + SlotText(11, "§aChips", chipsLore)
	private val mg = SkyBlockMenuParser.parse(d.title, garden)!!
	private val sizes = listOf(200, 240, 280, 320, 400, 420, 480, 520).flatMap { w -> listOf(180, 220, 256, 300).map { h -> MenuBounds(10, 10, w, h) } }

	private fun overlap(a: MenuBounds, c: MenuBounds) = a.x < c.x + c.w && c.x < a.x + a.w && a.y < c.y + c.h && c.y < a.y + a.h

	@Test
	fun compactWardrobeRightOfStatsAndApexLogoTopLeft() {
		for (b in sizes) for (model in listOf(m, mg)) {
			val (desk, cells1) = SkyBlockMenuLayout.deskButton(SkyBlockMenuLayout.cells(model, b), b, 22)!!
			val (wb, cells) = SkyBlockMenuLayout.wardrobeTile(cells1, b)!!
			val stats = cells.first { it.tile == SbTile.STATS }
			val (x0, cw) = SkyBlockMenuLayout.grid(b)
			val col4 = cells.first { it.tile == SbTile.LEVELING }
			val col2 = cells.first { it.tile == SbTile.COLLECTIONS }
			assertTrue(wb.w < col4.w || wb.w <= 24 + 48, "compact $b"); assertTrue(wb.w <= cw)
			assertEquals(col4.x + col4.w, wb.x + wb.w, "right edge on the grid $b")
			assertEquals(col2.x, stats.x, "Stats starts on column 2 $b")
			assertEquals(wb.x - SkyBlockMenuLayout.GAP, stats.x + stats.w, "Stats meets Wardrobe $b")
			assertEquals(stats.y, wb.y); assertEquals(stats.h, wb.h)
			if (stats.h >= SkyBlockMenuLayout.ICON_MIN_H) assertTrue(wb.w >= SkyBlockMenuLayout.TEXT_X + 48 || wb.w == cw, "label fits $b")
			val chips = model.chips?.let { SkyBlockMenuLayout.chipsButton(desk, b) }
			val ab = SkyBlockMenuLayout.apexButton(cells, b, chips?.x ?: desk.x)
			if (ab != null) {
				assertEquals(x0, ab.x, "on the grid's left edge $b"); assertEquals(ab.w, ab.h, "square")
				assertTrue(ab.y >= stats.y && ab.y + ab.h <= stats.y + stats.h, "in the row $b")
				val ic = SkyBlockMenuLayout.apexIcon(ab)
				assertTrue(ic.x > ab.x && ic.y > ab.y && ic.x + ic.w < ab.x + ab.w && ic.y + ic.h < ab.y + ab.h, "logo inside $b")
				assertFalse(overlap(ab, desk), "Desk $b"); chips?.let { assertFalse(overlap(ab, it), "chips $b") }
				assertFalse(overlap(ab, wb))
				for (c in cells) assertFalse(overlap(ab, c.bounds), "overlaps ${c.tile} $b")
			}
			for (c in cells) assertFalse(overlap(wb, c.bounds), "overlaps ${c.tile} $b")
			assertFalse(overlap(wb, desk), "overlaps Desk $b")
			assertTrue(desk.x >= x0 && desk.x + desk.w <= stats.x, "Desk in the top-left cell $b")
		}
		val b = MenuBounds(0, 0, 420, 256)
		val (desk, c) = SkyBlockMenuLayout.deskButton(SkyBlockMenuLayout.cells(m, b), b, 22)!!
		assertNotNull(SkyBlockMenuLayout.apexButton(SkyBlockMenuLayout.wardrobeTile(c, b)!!.second, b, desk.x), "Apex shows at the default size")
	}

	@Test
	fun apexClickOpensOnce() {
		var opened = 0; val pending = mutableListOf<() -> Unit>()
		SkyBlockMenuLayout.apexClick({ pending += it }) { opened++ }
		assertEquals(0, opened); pending.forEach { it() }; assertEquals(1, opened)
	}

	@Test
	fun wardrobeLabelIconAndCommand() {
		assertEquals("Wardrobe", SkyBlockMenuLayout.WARDROBE_LABEL)
		assertEquals("Wardrobe", SkyBlockMenuView.WARDROBE_LABEL.string)
		assertEquals("minecraft:leather_chestplate", SkyBlockMenuLayout.WARDROBE_ICON_ID)
		assertEquals(Items.LEATHER_CHESTPLATE, SkyBlockMenuView.WARDROBE_ICON.item)
		val sent = mutableListOf<String>(); val pending = mutableListOf<() -> Unit>()
		SkyBlockMenuLayout.wardrobeClick({ pending += it }, { sent += it })
		assertTrue(sent.isEmpty(), "not before the delay")
		pending.forEach { it() }
		assertEquals(listOf("wardrobe"), sent)
	}

	@Test
	fun wardrobeIsNotAGridTileAndLeavesCellsAlone() {
		assertTrue(SbTile.entries.none { it.label.equals("Wardrobe", true) }, "Hypixel has no Wardrobe slot in this menu")
		val b = MenuBounds(0, 0, 420, 256)
		val cells = SkyBlockMenuLayout.cells(m, b)
		assertEquals(cells, SkyBlockMenuLayout.deskButton(cells, b, 22)!!.second, "Stats not resized at the default size")
	}

	@Test
	fun deskUnchanged() {
		for (b in sizes) for (lw in listOf(22, 30)) {
			val cells = SkyBlockMenuLayout.cells(m, b)
			val stats = cells.first { it.tile == SbTile.STATS }
			val (desk, _) = SkyBlockMenuLayout.deskButton(cells, b, lw)!!
			val bh = minOf(13, stats.h)
			val need = b.x + SkyBlockMenuLayout.PAD + lw + 10 + SkyBlockMenuLayout.GAP
			val sx = if (stats.x >= need) stats.x else need
			assertEquals(MenuBounds(sx - SkyBlockMenuLayout.GAP - (lw + 10), stats.y + (stats.h - bh) / 2, lw + 10, bh), desk, "$b")

			assertEquals(cells, SkyBlockMenuLayout.cells(mg, b))
			assertEquals(desk, SkyBlockMenuLayout.deskButton(SkyBlockMenuLayout.cells(mg, b), b, lw)!!.first)
		}
		assertTrue(SkyBlockMenuLayout.deskEnabled(true)); assertFalse(SkyBlockMenuLayout.deskEnabled(false))
		assertEquals("Desk", SkyBlockMenuView.DESK_LABEL.string)
	}

	@Test
	fun chipsParsedFromTheRealSlotOnlyOnGarden() {
		assertNull(m.chips, "no chips item off the Garden")
		assertEquals(11, mg.chips?.slot)
		assertEquals(chipsLore, mg.chips?.lore, "tooltip comes from the real item")
		assertNull(mg.tiles.values.firstOrNull { it.first == 11 || it.first == 10 }, "not grid tiles")
		val alt = SkyBlockMenuParser.parse(d.title, d.slots.filter { it.slot != 11 } + SlotText(11, "§aManage Chips"))!!
		assertEquals(11, alt.chips?.slot)
		assertNull(SkyBlockMenuParser.parse(d.title, d.slots.filter { it.slot != 11 } + SlotText(11, "§aChipped Stone"))!!.chips)
	}

	@Test
	fun chipsButtonLeftOfDeskIconOnly() {
		for (b in sizes) {
			val cells0 = SkyBlockMenuLayout.cells(mg, b)
			val (desk, cells) = SkyBlockMenuLayout.deskButton(cells0, b, 22)!!
			val cb = SkyBlockMenuLayout.chipsButton(desk, b) ?: continue
			assertEquals(desk.x - SkyBlockMenuLayout.GAP - cb.w, cb.x, "left of Desk $b")
			assertEquals(SkyBlockMenuLayout.CHIPS_SIZE, cb.w); assertEquals(SkyBlockMenuLayout.CHIPS_SIZE, cb.h)
			assertEquals(desk.y + desk.h / 2, cb.y + cb.h / 2, "centred on Desk $b")
			assertTrue(cb.x >= b.x + SkyBlockMenuLayout.PAD)
			assertFalse(overlap(cb, desk))
			for (c in cells) assertFalse(overlap(cb, c.bounds), "overlaps ${c.tile} $b")
		}
		val b = MenuBounds(0, 0, 420, 256)
		val desk = SkyBlockMenuLayout.deskButton(SkyBlockMenuLayout.cells(mg, b), b, 22)!!.first
		assertNotNull(SkyBlockMenuLayout.chipsButton(desk, b), "fits at the default size")
		assertNull(SkyBlockMenuLayout.chipsButton(MenuBounds(b.x + SkyBlockMenuLayout.PAD + 5, 30, 32, 13), b), "no room = no button")
	}

	@Test
	fun chipsClickForwardsToTheRealSlot() {
		val clicks = mutableListOf<Pair<Int, Int>>()
		assertTrue(SkyBlockMenuLayout.chipsClick(mg, 0) { s, btn -> clicks += s to btn })
		assertTrue(SkyBlockMenuLayout.chipsClick(mg, 1) { s, btn -> clicks += s to btn })
		assertEquals(listOf(11 to 0, 11 to 1), clicks)
		assertFalse(SkyBlockMenuLayout.chipsClick(m, 0) { s, btn -> clicks += s to btn })
		assertEquals(2, clicks.size)
	}
}
