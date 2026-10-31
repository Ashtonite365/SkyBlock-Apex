// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.render.connected

import dev.ashtonite365.apex.features.render.connected.ConnectRules
import dev.ashtonite365.apex.features.render.connected.ConnectTiles
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN_LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.DOWN_RIGHT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.RIGHT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP_LEFT
import dev.ashtonite365.apex.features.render.connected.ConnectTiles.UP_RIGHT
import dev.ashtonite365.apex.features.render.connected.TileArt
import dev.ashtonite365.apex.features.render.connected.TileStyle
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
class ConnectSurfaceTilesTest {
	private val LINE_LIMIT = 1.5
	private val all = UP or UP_RIGHT or RIGHT or DOWN_RIGHT or DOWN or DOWN_LEFT or LEFT or UP_LEFT

	class Tex(val name: String, val size: Int, val px: IntArray)

	companion object {
		val BLOCK_TEXTURES = listOf(
			"gold_block", "iron_block", "diamond_block", "lapis_block", "netherite_block", "copper_block", "exposed_copper",
			"weathered_copper", "oxidized_copper", "smooth_stone", "quartz_block_side", "quartz_block_top", "polished_andesite",
			"polished_diorite", "polished_granite", "polished_deepslate", "polished_blackstone", "polished_tuff",
		)

		fun vanilla(name: String): Tex {
			val stream = ConnectSurfaceTilesTest::class.java.classLoader.getResourceAsStream("assets/minecraft/textures/block/$name.png")
				?: error("vanilla texture $name not on the test classpath")
			val img = stream.use { ImageIO.read(it) }
			val s = minOf(img.width, img.height)
			return Tex(name, s, IntArray(s * s) { img.getRGB(it % s, it / s) })
		}

		fun upscale(t: Tex, f: Int): Tex {
			val s = t.size * f
			return Tex(t.name + "@${s}", s, IntArray(s * s) { i -> t.px[(i / s / f) * t.size + (i % s) / f] })
		}

		fun bevel32(): Tex {
			val s = 32
			val rnd = java.util.Random(7)
			val px = IntArray(s * s) { val g = 150 + rnd.nextInt(20); argb(g, g - 20, 40) }
			for (y in 0 until s) for (x in 0 until s) {
				val d = minOf(x, y, s - 1 - x, s - 1 - y)
				val nearTopLeft = minOf(x, y) <= minOf(s - 1 - x, s - 1 - y)
				px[y * s + x] = when {
					d == 0 -> argb(60, 50, 20)
					d <= 3 -> if (nearTopLeft) argb(240 - d * 10, 220 - d * 10, 90) else argb(100 + d * 5, 80 + d * 5, 30)
					else -> px[y * s + x]
				}
			}
			return Tex("bevel32", s, px)
		}

		fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

		fun luma(p: Int) = 0.299 * (p ushr 16 and 255) + 0.587 * (p ushr 8 and 255) + 0.114 * (p and 255)
	}

	private fun interiorOf(t: Tex, b: IntArray): Set<Int> {
		val set = HashSet<Int>()
		for (y in b[TileArt.TOP] until t.size - b[TileArt.BOTTOM]) for (x in b[TileArt.LEFT] until t.size - b[TileArt.RIGHT]) set.add(t.px[y * t.size + x])
		return set
	}

	private fun allTextures(): List<Tex> = BLOCK_TEXTURES.map(::vanilla).let { v -> v + v.map { upscale(it, 2) } + bevel32() }

	@Test fun `block textures make surface tiles and glass keeps frame tiles`() {
		for (b in BLOCK_TEXTURES) assertEquals(TileStyle.SURFACE, ConnectRules.styleOf("minecraft:block/$b"), b)
		assertEquals(TileStyle.FRAME, ConnectRules.styleOf("minecraft:block/glass"))
		assertEquals(TileStyle.FRAME, ConnectRules.styleOf("minecraft:block/red_stained_glass"))
		val g = vanilla("glass")
		for (t in 0 until ConnectTiles.TILE_COUNT) {
			val key = ConnectTiles.keyOfTile(t)
			assertArrayEquals(TileArt.generate(g.px, g.size, key), TileArt.tile(TileStyle.FRAME, g.px, g.size, key), "glass tile $t")
		}
	}

	@Test fun `gold block bands cover the outline and the bevel line`() {
		val gold = vanilla("gold_block")
		val b = TileArt.bands(gold.px, 16)

		for (side in 0 until 4) assertTrue(b[side] >= 2, "side $side band ${b[side]}")
		for (side in 0 until 4) assertTrue(b[side] <= TileArt.maxBand(16), "side $side band ${b[side]}")
		val b32 = TileArt.bands(upscale(gold, 2).px, 32)
		for (side in 0 until 4) assertTrue(b32[side] >= 4, "32x side $side band ${b32[side]}")
		val bev = bevel32()
		val bb = TileArt.bands(bev.px, 32)
		for (side in 0 until 4) assertTrue(bb[side] >= 4, "bevel32 side $side band ${bb[side]}")
	}

	@Test fun `a dark inner shadow behind a plain line is still part of the band`() {

		val n = vanilla("netherite_block")
		val b = TileArt.bands(n.px, 16)
		assertTrue(b[TileArt.BOTTOM] >= 3, "bottom band ${b[TileArt.BOTTOM]}")
		assertTrue(b[TileArt.RIGHT] >= 3, "right band ${b[TileArt.RIGHT]}")
		val b32 = TileArt.bands(upscale(n, 2).px, 32)
		assertTrue(b32[TileArt.BOTTOM] >= 6 && b32[TileArt.RIGHT] >= 6, "32x bands ${b32.toList()}")
	}

	@Test fun `a fully joined tile is only the middle pattern`() {
		for (t in allTextures()) {
			val b = TileArt.bands(t.px, t.size)
			val inner = interiorOf(t, b)
			val tile = TileArt.tile(TileStyle.SURFACE, t.px, t.size, ConnectTiles.keyOf(all), b)
			for (i in tile.indices) assertTrue(tile[i] in inner, "${t.name}: pixel ${i % t.size},${i / t.size} is not from the middle")
		}
	}

	@Test fun `the pixels on both sides of a seam come from the middle`() {
		for (t in allTextures()) {
			val s = t.size
			val b = TileArt.bands(t.px, s)
			val inner = interiorOf(t, b)

			val west = TileArt.tile(TileStyle.SURFACE, t.px, s, ConnectTiles.keyOf(RIGHT), b)
			val east = TileArt.tile(TileStyle.SURFACE, t.px, s, ConnectTiles.keyOf(LEFT), b)
			val above = TileArt.tile(TileStyle.SURFACE, t.px, s, ConnectTiles.keyOf(DOWN), b)
			val below = TileArt.tile(TileStyle.SURFACE, t.px, s, ConnectTiles.keyOf(UP), b)
			val wTop = b[TileArt.TOP]; val wBot = b[TileArt.BOTTOM]; val wL = b[TileArt.LEFT]; val wR = b[TileArt.RIGHT]
			for (y in wTop until s - wBot) {
				for (x in s - wR until s) assertTrue(west[y * s + x] in inner, "${t.name}: west tile seam pixel $x,$y")
				for (x in 0 until wL) assertTrue(east[y * s + x] in inner, "${t.name}: east tile seam pixel $x,$y")
			}
			for (x in wL until s - wR) {
				for (y in s - wBot until s) assertTrue(above[y * s + x] in inner, "${t.name}: upper tile seam pixel $x,$y")
				for (y in 0 until wTop) assertTrue(below[y * s + x] in inner, "${t.name}: lower tile seam pixel $x,$y")
			}

			val colMeans = (wL until s - wR).map { x -> (wTop until s - wBot).sumOf { y -> luma(t.px[y * s + x]) } / (s - wTop - wBot) }
			val lo = colMeans.min() - 1.0; val hi = colMeans.max() + 1.0
			for (x in s - wR until s) {
				val m = (wTop until s - wBot).sumOf { y -> luma(west[y * s + x]) } / (s - wTop - wBot)
				assertTrue(m in lo..hi, "${t.name}: seam column $x mean $m outside the middle's $lo..$hi")
			}
		}
	}

	@Test fun `an edge that is not joined keeps its border, and it runs on past a joined edge`() {
		for (t in allTextures()) {
			val s = t.size
			val b = TileArt.bands(t.px, s)
			val tile = TileArt.tile(TileStyle.SURFACE, t.px, s, ConnectTiles.keyOf(UP or DOWN), b)
			val f = TileArt.surfaceField(t.px, s, b)
			for (y in f.reachT until s - f.reachB) {
				for (x in 0 until b[TileArt.LEFT]) assertEquals(t.px[y * s + x], tile[y * s + x], "${t.name} left border $x,$y")
				for (x in s - b[TileArt.RIGHT] until s) assertEquals(t.px[y * s + x], tile[y * s + x], "${t.name} right border $x,$y")
			}

			for (y in (0 until f.reachT) + (s - f.reachB until s)) for (x in (0 until b[TileArt.LEFT]) + (s - b[TileArt.RIGHT] until s)) {
				val column = (b[TileArt.TOP] until s - b[TileArt.BOTTOM]).map { t.px[it * s + x] }.toSet()
				assertTrue(tile[y * s + x] in column, "${t.name}: top band at border column $x is not that column's border")
			}
			assertArrayEquals(t.px, TileArt.tile(TileStyle.SURFACE, t.px, s, 0, b), "${t.name}: tile 0 must be the texture")
		}
	}

	@Test fun `an inner corner keeps the texture's own corner`() {
		val t = vanilla("gold_block")
		val b = TileArt.bands(t.px, 16)
		val tile = TileArt.tile(TileStyle.SURFACE, t.px, 16, ConnectTiles.keyOf(UP or LEFT), b)
		for (y in 0 until b[TileArt.TOP]) for (x in 0 until b[TileArt.LEFT]) assertEquals(t.px[y * 16 + x], tile[y * 16 + x])
	}

	@Test fun `every one of the 47 tiles has the texture's size and only its pixels`() {
		for (t in allTextures()) {
			val b = TileArt.bands(t.px, t.size)
			val colours = t.px.toHashSet()
			for (n in 0 until ConnectTiles.TILE_COUNT) {
				val tile = TileArt.tile(TileStyle.SURFACE, t.px, t.size, ConnectTiles.keyOfTile(n), b)
				assertEquals(t.size * t.size, tile.size)
				assertTrue(tile.all { it in colours }, "${t.name} tile $n")
			}
		}
	}

	private fun wrapTile(src: IntArray, size: Int, key: Int, bands: IntArray): IntArray {
		val wt = bands[TileArt.TOP]; val wr = bands[TileArt.RIGHT]; val wb = bands[TileArt.BOTTOM]; val wl = bands[TileArt.LEFT]
		val t = key and ConnectTiles.K_TOP != 0; val r = key and ConnectTiles.K_RIGHT != 0
		val bo = key and ConnectTiles.K_BOTTOM != 0; val l = key and ConnectTiles.K_LEFT != 0
		val nx = size - wl - wr; val ny = size - wt - wb
		val out = IntArray(size * size)
		for (y in 0 until size) {
			val top = y < wt; val bottom = y >= size - wb
			val sy = if ((top && t) || (bottom && bo)) wt + Math.floorMod(y - wt, ny) else y
			for (x in 0 until size) {
				val left = x < wl; val right = x >= size - wr
				val inner = (top && left && key and ConnectTiles.K_CORNER_TL != 0) || (top && right && key and ConnectTiles.K_CORNER_TR != 0) ||
					(bottom && right && key and ConnectTiles.K_CORNER_BR != 0) || (bottom && left && key and ConnectTiles.K_CORNER_BL != 0)
				if (inner) { out[y * size + x] = src[y * size + x]; continue }
				val sx = if ((left && l) || (right && r)) wl + Math.floorMod(x - wl, nx) else x
				out[y * size + x] = src[sy * size + sx]
			}
		}
		return out
	}

	private fun colStep(img: IntArray, size: Int, c: Int): Double =
		(0 until size).sumOf { y -> kotlin.math.abs(luma(img[y * size + c]) - luma(img[y * size + (c + 1) % size])) } / size

	private fun rowStep(img: IntArray, size: Int, r: Int): Double =
		(0 until size).sumOf { x -> kotlin.math.abs(luma(img[r * size + x]) - luma(img[((r + 1) % size) * size + x])) } / size

	private fun lineScore(img: IntArray, t: Tex, b: IntArray): Double {
		val s = t.size
		val l = b[TileArt.LEFT]; val r = s - b[TileArt.RIGHT]; val tp = b[TileArt.TOP]; val bt = s - b[TileArt.BOTTOM]
		var ownCol = 0.0; var ownRow = 0.0
		for (c in l until r - 1) ownCol = maxOf(ownCol, (tp until bt).sumOf { y -> kotlin.math.abs(luma(t.px[y * s + c]) - luma(t.px[y * s + c + 1])) } / (bt - tp))
		for (rr in tp until bt - 1) ownRow = maxOf(ownRow, (l until r).sumOf { x -> kotlin.math.abs(luma(t.px[rr * s + x]) - luma(t.px[(rr + 1) * s + x])) } / (r - l))
		val col = (0 until s).maxOf { colStep(img, s, it) }
		val row = (0 until s).maxOf { rowStep(img, s, it) }
		return maxOf(col / maxOf(ownCol, 3.0), row / maxOf(ownRow, 3.0))
	}

	@Test fun `a fully joined surface has no seam or repeat line`() {
		val report = StringBuilder()
		var oldWorse = 0
		val bad = ArrayList<String>()
		val textures = allTextures()
		for (t in textures) {
			val b = TileArt.bands(t.px, t.size)
			val key = ConnectTiles.keyOf(all)
			val now = lineScore(TileArt.tile(TileStyle.SURFACE, t.px, t.size, key, b), t, b)
			val before = lineScore(wrapTile(t.px, t.size, key, b), t, b)
			report.append("${t.name}: 2f4ec5bb %.2f, now %.2f\n".format(before, now))
			if (now > LINE_LIMIT) bad += t.name
			if (before > now + 0.02) oldWorse++
		}
		println(report)
		assertTrue(bad.isEmpty(), "a line stands out on $bad:\n$report")
		assertTrue(oldWorse >= textures.size / 2, "the new tiles should beat the 2f4ec5bb ones on most textures:\n$report")
	}

	@Test fun `every joined block boundary in a wall is as smooth as the inside of a block`() {
		for (t in allTextures()) {
			val s = t.size
			val b = TileArt.bands(t.px, s)
			val field = TileArt.surfaceField(t.px, s, b)
			val rows = layout.size; val cols = layout[0].length
			val w = cols * s
			val img = IntArray(w * rows * s)
			val solid = BooleanArray(img.size)
			for (r in 0 until rows) for (c in 0 until cols) {
				if (layout[r][c] != '#') continue
				val px = TileArt.tile(TileStyle.SURFACE, t.px, s, ConnectTiles.keyOfTile(ConnectTiles.tileOf(maskAt(r, c))), b, field)
				for (y in 0 until s) for (x in 0 until s) { val i = (r * s + y) * w + c * s + x; img[i] = px[y * s + x]; solid[i] = true }
			}
			fun step(i: Int, j: Int) = kotlin.math.abs(luma(img[i]) - luma(img[j]))

			val l = field.reachL; val rr = s - field.reachR; val tp = field.reachT; val bt = s - field.reachB
			val colIn = DoubleArray(s); var across = 0.0; var n = 0
			for (r in 0 until rows) for (c in 0 until cols - 1) {
				if (layout[r][c] != '#' || layout[r][c + 1] != '#') continue
				n++
				for (y in tp until bt) {
					val row = (r * s + y) * w
					across += step(row + c * s + s - 1, row + c * s + s)
					for (x in l until rr - 1) colIn[x] += step(row + c * s + x, row + c * s + x + 1)
				}
			}
			val sideAcross = across / (n * (bt - tp)); val sideInside = colIn.max() / (n * (bt - tp))
			assertTrue(sideAcross <= sideInside * 1.25 + 1.0, "${t.name}: joined side boundaries step %.2f, strongest inside %.2f".format(sideAcross, sideInside))

			val rowIn = DoubleArray(s); across = 0.0; n = 0
			for (r in 0 until rows - 1) for (c in 0 until cols) {
				if (layout[r][c] != '#' || layout[r + 1][c] != '#') continue
				n++
				for (x in l until rr) {
					across += step((r * s + s - 1) * w + c * s + x, (r * s + s) * w + c * s + x)
					for (y in tp until bt - 1) rowIn[y] += step((r * s + y) * w + c * s + x, (r * s + y + 1) * w + c * s + x)
				}
			}
			val upAcross = across / (n * (rr - l)); val upInside = rowIn.max() / (n * (rr - l))
			assertTrue(upAcross <= upInside * 1.25 + 1.0, "${t.name}: joined top/bottom boundaries step %.2f, strongest inside %.2f".format(upAcross, upInside))
		}
	}

	@Test fun `the seamless field agrees with the texture away from the edges it continues across`() {
		for (t in allTextures()) {
			val s = t.size
			val b = TileArt.bands(t.px, s)
			val f = TileArt.surfaceField(t.px, s, b)
			for (y in 0 until s) for (x in 0 until s) {
				val i = y * s + x
				val xFree = x >= f.reachL && x < s - f.reachR
				val yFree = y >= f.reachT && y < s - f.reachB
				if (xFree) { assertEquals(t.px[i], f.h[i], "${t.name} h $x,$y"); assertEquals(f.v[i], f.o[i], "${t.name} o/v $x,$y") }
				if (yFree) { assertEquals(t.px[i], f.v[i], "${t.name} v $x,$y"); assertEquals(f.h[i], f.o[i], "${t.name} o/h $x,$y") }
			}

			assertTrue(f.reachL >= b[TileArt.LEFT] && f.reachR >= b[TileArt.RIGHT] && f.reachT >= b[TileArt.TOP] && f.reachB >= b[TileArt.BOTTOM])
			assertTrue(f.reachL + f.reachR < s && f.reachT + f.reachB < s)
		}
	}

	private val layout = listOf(
		"######",
		"######",
		"##.###",
		"######",
		"###...",
	)

	private fun maskAt(r: Int, c: Int): Int {
		fun at(rr: Int, cc: Int) = rr in layout.indices && cc in layout[rr].indices && layout[rr][cc] == '#'
		var m = 0
		for (i in 0 until 8) if (at(r - ConnectTiles.SLOT_UP[i], c + ConnectTiles.SLOT_RIGHT[i])) m = m or (1 shl i)
		return m
	}

	private fun render(t: Tex, tileOf: (Int) -> IntArray, scale: Int): BufferedImage {
		val s = t.size
		val rows = layout.size; val cols = layout[0].length
		val img = BufferedImage(cols * s * scale, rows * s * scale, BufferedImage.TYPE_INT_ARGB)
		for (r in 0 until rows) for (c in 0 until cols) {
			if (layout[r][c] != '#') continue
			val px = tileOf(ConnectTiles.keyOfTile(ConnectTiles.tileOf(maskAt(r, c))))
			for (y in 0 until s * scale) for (x in 0 until s * scale)
				img.setRGB(c * s * scale + x, r * s * scale + y, px[(y / scale) * s + x / scale])
		}
		return img
	}

	@Test fun `write before and after previews`() {
		val dir = File(System.getProperty("user.dir"), "connblocks-preview").apply { mkdirs() }
		val textures = listOf("gold_block", "iron_block", "diamond_block", "copper_block", "netherite_block", "lapis_block", "smooth_stone", "quartz_block_side", "polished_andesite", "polished_deepslate", "weathered_copper", "polished_granite")
			.map(::vanilla).let { it + upscale(it[0], 2) + bevel32() }
		val sheets = ArrayList<BufferedImage>()
		for (t in textures) {
			val scale = if (t.size >= 32) 3 else 6
			val b = TileArt.bands(t.px, t.size)
			val field = TileArt.surfaceField(t.px, t.size, b)
			run {

				val n = t.size
				val img = BufferedImage(3 * n * scale, 3 * n * scale, BufferedImage.TYPE_INT_ARGB)
				for (y in 0 until img.height) for (x in 0 until img.width) img.setRGB(x, y, field.o[((y / scale) % n) * n + (x / scale) % n])
				ImageIO.write(img, "png", File(dir, "v2-${t.name.replace('@', '_')}-field-3x3.png"))
			}
			val before = render(t, { key -> wrapTile(t.px, t.size, key, b) }, scale)
			val after = render(t, { key -> TileArt.tile(TileStyle.SURFACE, t.px, t.size, key, b, field) }, scale)
			val gap = 24
			val pair = BufferedImage(before.width * 2 + gap, before.height, BufferedImage.TYPE_INT_ARGB)
			val g = pair.createGraphics()
			g.color = java.awt.Color(40, 40, 48); g.fillRect(0, 0, pair.width, pair.height)
			g.drawImage(before, 0, 0, null); g.drawImage(after, before.width + gap, 0, null); g.dispose()
			ImageIO.write(pair, "png", File(dir, "v2-${t.name.replace('@', '_')}-before-after.png"))
			sheets.add(pair)
		}
		val w = sheets.maxOf { it.width }
		val all = BufferedImage(w, sheets.sumOf { it.height + 16 }, BufferedImage.TYPE_INT_ARGB)
		val g = all.createGraphics()
		g.color = java.awt.Color(40, 40, 48); g.fillRect(0, 0, all.width, all.height)
		var y = 0
		for (sh in sheets) { g.drawImage(sh, 0, y, null); y += sh.height + 16 }
		g.dispose()
		ImageIO.write(all, "png", File(dir, "v2-all-before-after.png"))
		assertTrue(File(dir, "v2-gold_block-before-after.png").length() > 0)
	}
}
