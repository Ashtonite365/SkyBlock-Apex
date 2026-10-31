// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.features.menus.style

import dev.ashtonite365.apex.features.menus.style.ForgeMode
import dev.ashtonite365.apex.features.menus.style.MenuAnim
import dev.ashtonite365.apex.features.menus.style.Press
import dev.ashtonite365.apex.features.menus.style.RecordingCanvas
import dev.ashtonite365.apex.features.menus.style.Shapes
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleCanvas
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.features.menus.style.StyleWidgets
import dev.ashtonite365.apex.features.menus.style.ForgeMetal
import dev.ashtonite365.apex.features.menus.style.StyledChestLayout
import dev.ashtonite365.apex.features.menus.style.WardrobeLayout
import dev.ashtonite365.apex.gui.config.styled.StyledSettingsLayout
import dev.ashtonite365.apex.test.ours.OursGolden
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class StylePreviewTest {
	private val W = 640; private val H = 360

	private val looks = listOf(
		"skyglass" to StyleState(),
		"forge-dark" to StyleState(style = Style.FORGE, forgeMode = ForgeMode.DARK),
		"forge-light" to StyleState(style = Style.FORGE, forgeMode = ForgeMode.LIGHT),
	)

	private fun world(c: StyleCanvas) {

		for (y in 0 until H step 12) c.fill(0, y, W, y + 12, if ((y / 12) % 2 == 0) 0xFF5C8A4E.toInt() else 0xFF6E9A5A.toInt())
		c.fill(0, 0, W, 90, 0xFF8EC3EE.toInt())
	}

	private fun chrome(c: StyleCanvas, p: StylePalette, s: StyleState, x: Int, y: Int, w: Int, title: String) {
		c.text(title, x + 8, y + 6, p.text)
		StyleBar().draw(c, p, s, StyleBar.geometry(x + w - 4, y + 3, s), -1, -1, false)
	}

	private fun chest(c: StyleCanvas, p: StylePalette, s: StyleState, rows: Int, inv: Boolean, title: String) {
		c.fill(0, 0, W, H, p.backdrop)
		val l = StyledChestLayout.build(W, H, rows, inv)
		val f = l.frame
		StyleWidgets.card(c, p, f.x, f.y, f.w, f.h); ForgeMetal.plate(c, p, f.x, f.y, f.w, f.h)
		chrome(c, p, s, f.x, f.y, f.w, title)
		for (i in 0 until rows * 9) {
			val b = l.cellScreen(i)
			StyleWidgets.cell(c, p, b.x, b.y, b.w, i == 12)
			if (i % 7 == 3) c.itemHint(b.x + 1, b.y + 1, b.w - 2, "item")
		}
		l.inventory?.let { (ix, iy) ->
			c.text("Inventory", ix, iy, p.textDim)
			for (i in 0 until 36) {
				val cx = ix + (i % 9) * 18; val cy = iy + 10 + (i / 9) * 18 + if (i >= 27) 4 else 0
				StyleWidgets.cell(c, p, cx, cy, 18, false)
			}
		}
	}

	private fun wardrobe(c: StyleCanvas, p: StylePalette, s: StyleState) {
		c.fill(0, 0, W, H, p.backdrop)
		val f = WardrobeLayout.frame(W, H)
		StyleWidgets.card(c, p, f.x, f.y, f.w, f.h); ForgeMetal.plate(c, p, f.x, f.y, f.w, f.h)
		chrome(c, p, s, f.x, f.y, f.w, "Wardrobe (1/2)")
		val k = WardrobeLayout.INVENTORY_SCALE
		val IP = dev.ashtonite365.apex.features.menus.support.InventoryPanel
		val bottom = f.y + f.h - 22 - (IP.scaledHeight(k) + 4)
		val a = WardrobeLayout.armour(f.x, f.w, f.y + 22, bottom)
		for (r in 0 until 4) for (col in 0 until 9) {
			val (x, y) = a.cellAt(r, col)
			StyleWidgets.cell(c, p, x, y, a.cell, r == 1 && col == 2)
			if (col % 3 == 0) a.itemPos(x, y).let { (ix, iy) -> c.itemHint(ix, iy, Math.round(16 * a.itemScale), "armour") }
		}
		for (col in 0 until 9) {
			val (bx, by) = a.buttonAt(col)
			StyleWidgets.button(c, p, bx, by, WardrobeLayout.BUTTON, WardrobeLayout.BUTTON, "${col + 1}", if (col == 0) Press.ACTIVE else if (col == 3) Press.HOVER else Press.NORMAL)
		}
		val ix = f.x + (f.w - 9 * Math.round(18 * k)) / 2; val iy = bottom + 2
		c.text("Inventory", ix, iy, p.textDim)
		for (i in 0 until 36) { val (cx, cy, sz) = IP.scaledCell(i / 9, i % 9, ix, iy, k); StyleWidgets.cell(c, p, cx, cy, sz, false)
			if (i % 5 == 1) c.itemHint(cx + Math.round(k), cy + Math.round(k), Math.round(16 * k), "item") }
	}

	private fun settings(c: StyleCanvas, p: StylePalette, s: StyleState, dockHover: Boolean) {
		c.fill(0, 0, W, H, p.backdrop)
		val x = 20; val y = 16; val w = W - 40; val h = H - 32
		StyleWidgets.card(c, p, x, y, w, h); ForgeMetal.plate(c, p, x, y, w, h)
		chrome(c, p, s, x, y, w, "Apex Settings")
		var bodyX = x + 8
		if (s.style == Style.SKYGLASS) {
			val dw = StyledSettingsLayout.dockWidth(dockHover, if (dockHover) 1000 else 0)
			Shapes.round(c, x + 6, y + StyledSettingsLayout.HEADER, dw, h - StyledSettingsLayout.HEADER - 6, p.dockRadius, p.raised)
			for ((i, n) in listOf("Look & Feel", "Menus", "Dungeons", "Mining", "Profile Viewer").withIndex()) {
				val iy = y + StyledSettingsLayout.HEADER + 6 + i * 22
				Shapes.round(c, x + 10, iy, dw - 8, 18, p.controlRadius, if (i == 0) p.accent else p.buttonFill)
				c.text(if (dw > 60) n else n.take(1), x + 15, iy + 5, if (i == 0) p.textOnAccent else p.text)
			}
			bodyX = x + 6 + dw + 8
		} else {
			var tx = x + 8
			for ((i, n) in listOf("Look & Feel", "Menus", "Dungeons", "Mining").withIndex()) {
				val tw = c.textWidth(n) + 14
				StyleWidgets.button(c, p, tx, y + StyledSettingsLayout.HEADER, tw, StyledSettingsLayout.TAB_H, n, if (i == 0) Press.ACTIVE else Press.NORMAL)
				tx += tw + 4
			}
		}
		val cy = y + StyledSettingsLayout.HEADER + if (s.style == Style.FORGE) StyledSettingsLayout.TAB_H + 6 else 6
		val cw = x + w - 8 - bodyX
		StyleWidgets.card(c, p, bodyX, cy, cw, 6 * StyledSettingsLayout.ROW + StyledSettingsLayout.CARD_HEAD + 8, fill = p.raised)
		c.text("Look & Feel", bodyX + 8, cy + 5, p.text)
		for ((i, n) in listOf("Style", "Forge dark", "Style every chest menu", "Animations", "Animation", "Reduce motion").withIndex()) {
			val ry = cy + StyledSettingsLayout.CARD_HEAD + 4 + i * StyledSettingsLayout.ROW
			c.text(n, bodyX + 10, ry + 6, p.text)
			when (i) {
				0 -> StyleWidgets.button(c, p, bodyX + cw - 90, ry + 2, 80, 16, s.style.label, Press.HOVER)
				4 -> StyleWidgets.button(c, p, bodyX + cw - 90, ry + 2, 80, 16, s.anim.label, Press.PRESSED)
				else -> StyleWidgets.toggle(c, p, bodyX + cw - 40, ry + 4, if (i == 5) 0.0 else 1.0)
			}
		}
	}

	private fun anim(c: StyleCanvas, p: StylePalette, s: StyleState, kind: MenuAnim) {
		chest(c, p, s, 4, false, "Open: ${kind.label}")
		val l = StyledChestLayout.build(W, H, 4, false).frame
		StyleWidgets.cover(c, kind, p.backdrop or 0xFF000000.toInt(), l.x, l.y, l.w, l.h, 0.5, 7L, 120L)
	}

	private fun storage(c: StyleCanvas, p: StylePalette, s: StyleState) {
		c.fill(0, 0, W, H, p.backdrop)
		c.text("Storage", 24, 10, p.text)
		StyleBar().draw(c, p, s, StyleBar.geometry(W - 24, 6, s), -1, -1, false)
		val names = listOf("Ender Chest 1", "Ender Chest 2", "Ender Chest 3", "Backpack 1", "Backpack 2", "Backpack 3")
		for ((i, n) in names.withIndex()) {
			val rows = if (i < 3) 5 else 3
			val x = 24 + (i % 3) * (9 * 18 + 16); val y = 26 + (i / 3) * (5 * 18 + 26)
			dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewStyle.pageCard(c, p, x - 3, y - 3, 9 * 18 + 6, rows * 18 + 16, i == 1)
			c.text(n, x, y, p.text)
			for (k in 0 until rows * 9) {
				val cx = x + (k % 9) * 18; val cy = y + 11 + (k / 9) * 18
				c.fill(cx, cy, cx + 17, cy + 17, dev.ashtonite365.apex.features.inventory.storageoverlay.StorageOverviewStyle.cellFill(p))
				if ((k * 7 + i) % 5 == 0) c.itemHint(cx + 1, cy + 1, 15, "item")
			}
		}
	}

	private fun navMenu(c: StyleCanvas, p: StylePalette, s: StyleState) {
		c.fill(0, 0, W, H, p.backdrop)
		val l = StyledChestLayout.build(W, H, 6, false); val f = l.frame
		StyleWidgets.card(c, p, f.x, f.y, f.w, f.h); ForgeMetal.plate(c, p, f.x, f.y, f.w, f.h)
		chrome(c, p, s, f.x, f.y, f.w, "Essence Guide")
		val G = dev.ashtonite365.apex.features.menus.generic.GenericWrapper
		val items = setOf(4, 19, 20, 21, 22, 23, 24, 25, 48, 49)
		val panes = listOf(dev.ashtonite365.apex.features.menus.generic.GenericWrapper.PaneIn(10, true, "§aEssence", listOf("§7Essence drops from dungeon", "§7bosses and minions.", "§7bosses and minions.")),
			dev.ashtonite365.apex.features.menus.generic.GenericWrapper.PaneIn(16, true, "§aEssence", listOf("§7Essence drops from dungeon", "§7bosses and minions.")))
		val plan = if (s.style == Style.SKYGLASS) G.planPanes(panes) else emptyMap()
		val hide = G.hideEmptyCells(s.style == Style.SKYGLASS, "Essence Guide")
		for (i in 0 until 54) {
			val b = l.cellScreen(i)
			when (val pl = plan[i]) {
				is dev.ashtonite365.apex.features.menus.generic.GenericWrapper.Plan.Text -> {
					pl.lines.forEachIndexed { n, t -> c.text(t.replace(Regex("§."), ""), b.x + 1, b.y + 1 + n * 9, p.text) }; continue }
				dev.ashtonite365.apex.features.menus.generic.GenericWrapper.Plan.Hidden -> continue
				else -> {}
			}
			val filled = i in items || i in setOf(10, 16)
			if (!filled && hide) continue
			StyleWidgets.cell(c, p, b.x, b.y, b.w, false)
			if (filled) c.itemHint(b.x + 1, b.y + 1, b.w - 2, "item")
		}
	}

	private fun scenes(): List<Pair<String, (StyleCanvas) -> Unit>> = looks.flatMap { (name, s) ->
		val p = StylePalette.of(s)
		listOf<Pair<String, (StyleCanvas) -> Unit>>(
			"$name-chest-6rows-grown" to { c -> world(c); chest(c, p, s, 6, false, "Skyblock Menu") },
			"$name-chest-3rows-inventory" to { c -> world(c); chest(c, p, s, 3, true, "Salvage Item") },
			"$name-wardrobe" to { c -> world(c); wardrobe(c, p, s) },
			"$name-settings" to { c -> world(c); settings(c, p, s, true) },
			"$name-storage-view" to { c -> world(c); storage(c, p, s) },
			"$name-nav-menu" to { c -> world(c); navMenu(c, p, s) },
		) + MenuAnim.entries.map { k -> "$name-open-${k.id}" to { c: StyleCanvas -> world(c); anim(c, p, s, k) } }
	}

	@Test
	fun previews() {
		val dir = File(System.getenv("APEX_PREVIEW_DIR") ?: "build/previews").apply { mkdirs() }
		val golden = ArrayList<Pair<String, String>>()
		for ((name, draw) in scenes()) {
			val rec = RecordingCanvas(); draw(rec)
			golden += name to rec.ops.joinToString("\n")

			val frost = name.startsWith("skyglass")
			ImageIO.write(raster(rec.ops, if (frost) worldOps else 0), "png", File(dir, "$name.png"))
		}
		OursGolden.checkDigests("menus-1031-style-previews", golden)
	}

	private val worldOps = RecordingCanvas().also { world(it) }.ops.size

	private fun raster(ops: List<String>, blurAfter: Int = 0): BufferedImage {
		val k = 2
		var img = BufferedImage(W * k, H * k, BufferedImage.TYPE_INT_ARGB)
		var g = img.createGraphics()
		if (blurAfter > 0) {
			paint(g, ops.take(blurAfter), k); g.dispose()
			val n = 7; val kern = java.awt.image.Kernel(n, n, FloatArray(n * n) { 1f / (n * n) })
			img = java.awt.image.ConvolveOp(kern, java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(img, null)
			g = img.createGraphics()
		}
		paint(g, ops.drop(blurAfter), k)
		g.dispose(); return img
	}

	private fun paint(g: java.awt.Graphics2D, ops: List<String>, k: Int) {
		g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 8 * k)
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
		for (op in ops) {
			val t = op.split(" ")
			when (t[0]) {
				"F" -> { g.color = Color(java.lang.Long.parseLong(t[5], 16).toInt(), true); g.fillRect(t[1].toInt() * k, t[2].toInt() * k, (t[3].toInt() - t[1].toInt()) * k, (t[4].toInt() - t[2].toInt()) * k) }
				"T" -> { g.color = Color(java.lang.Long.parseLong(t[3], 16).toInt(), true); g.drawString(t.drop(5).joinToString(" ").replace(Regex("§."), ""), t[1].toInt() * k, (t[2].toInt() + 7) * k) }
				"I" -> { g.color = Color(0xB0C08040.toInt(), true); val n = t[3].toInt(); g.fillRect((t[1].toInt() + n / 6) * k, (t[2].toInt() + n / 6) * k, n * 2 / 3 * k, n * 2 / 3 * k) }
			}
		}
	}
}
