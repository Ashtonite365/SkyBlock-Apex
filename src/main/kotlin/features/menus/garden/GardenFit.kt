// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.garden

import kotlin.math.ceil
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.ashtonite365.apex.features.menus.support.EnchantLabel

data class FitTile(val name: String, val body: List<String>)

data class FitPlan(val cols: Int, val tileW: Int, val scale: Float, val showBody: Boolean, val rowHeights: List<Int>, val gap: Int, val iconW: Int) {
	val height get() = rowHeights.sum() + gap * (rowHeights.size - 1).coerceAtLeast(0)

	val textW get() = ((tileW - 4 - iconW) / scale).toInt()
}

object GardenFit {
	const val LINE = 9
	val SCALES = (20 downTo 10).map { it / 20f }

	fun wrap(s: String, width: Int, measure: (String) -> Int): List<String> {
		if (s.isBlank()) return listOf("")
		val st = Regex("^(§.)+").find(s)?.value.orEmpty()
		return EnchantLabel.wrap(s.removePrefix(st), width) { measure(st + it) }.map { st + it }
	}

	fun lines(t: FitTile, textW: Int, showBody: Boolean, measure: (String) -> Int): List<String> {
		val body = if (showBody) t.body.flatMap { wrap(it, textW, measure) }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() } else emptyList()
		return wrap(t.name, textW, measure) + body
	}

	fun tileH(lines: Int, scale: Float, iconW: Int) = maxOf(if (iconW > 0) minOf(18, iconW + 3) else 0, 3 + ceil(lines * LINE * scale).toInt() + 2)

	fun plan(tiles: List<FitTile?>, viewW: Int, viewH: Int, measure: (String) -> Int, cols: Int? = null, gap: Int = 2, iconW: Int = 18, allowHideBody: Boolean = true, prefer: Int? = null): FitPlan? {
		val n = tiles.size
		if (n == 0) return FitPlan(1, viewW, 1f, true, emptyList(), gap, iconW)
		for (showBody in if (allowHideBody) listOf(true, false) else listOf(true)) for (s in SCALES) for (c in cols?.let { listOf(it) } ?: (1..n).sortedBy { c -> prefer?.let { kotlin.math.abs(c - it) } ?: c }) {
			val tw = (viewW - (c - 1) * gap) / c
			if (tw - 4 - iconW < 16) continue
			val tw0 = ((tw - 4 - iconW) / s).toInt()
			val rows = tiles.chunked(c).map { row -> row.maxOf { t -> t?.let { tileH(lines(it, tw0, showBody, measure).size, s, iconW) } ?: 0 } }
			val p = FitPlan(c, tw, s, showBody, rows, gap, iconW)
			if (p.height <= viewH) return p
		}
		return null
	}

	fun iconSize(iconW: Int) = (iconW - 2).coerceIn(0, 16)

	fun drawIcon(ctx: GuiGraphicsExtractor, stack: net.minecraft.world.item.ItemStack, x: Int, y: Int, size: Int) {
		if (size <= 0 || stack.isEmpty) return
		ctx.pose().pushMatrix(); ctx.pose().translate(x.toFloat(), y.toFloat()); ctx.pose().scale(size / 16f, size / 16f)
		ctx.item(stack, 0, 0)
		ctx.pose().popMatrix()
	}

	fun drawLines(ctx: GuiGraphicsExtractor, font: Font, lines: List<String>, x: Int, y: Int, scale: Float, colour: Int) {
		ctx.pose().pushMatrix(); ctx.pose().translate(x.toFloat(), y.toFloat()); ctx.pose().scale(scale, scale)
		lines.forEachIndexed { k, l -> ctx.text(font, l, 0, k * LINE, colour) }
		ctx.pose().popMatrix()
	}
}
