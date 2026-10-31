package dev.ashtonite365.apex.gui

import io.github.notenoughupdates.moulconfig.common.MyResourceLocation
import io.github.notenoughupdates.moulconfig.gui.GuiComponent
import io.github.notenoughupdates.moulconfig.gui.GuiImmediateContext
import io.github.notenoughupdates.moulconfig.observer.GetSetter
import io.github.notenoughupdates.moulconfig.platform.MoulConfigRenderContext
import me.shedaniel.math.Color
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import dev.ashtonite365.apex.Apex

class BarComponent(val progress: GetSetter<Double>, val total: GetSetter<Double>, val fillColor: Color, val emptyColor: Color) : GuiComponent() {
	override fun getWidth() = 80
	override fun getHeight() = 8

	data class Texture(val identifier: Identifier, val u1: Float, val v1: Float, val u2: Float, val v2: Float) {

		fun draw(context: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, color: Color) = context.innerBlit(RenderPipelines.GUI_TEXTURED, identifier, x, y, x + width, x + height, u1, u2, v1, v2, color.color)

		fun strip(px: Int) = copy(u1 = u1 + px / SHEET_PX, u2 = u1 + (px + 1) / SHEET_PX)
	}

	companion object Sheet {
		private const val SHEET_PX = 64F
		private const val CELL = 4
		val resource = Apex.identifier("textures/gui/bar.png")
		private fun sheetSlice(fromPx: Int, toPx: Int) = Texture(resource, fromPx / SHEET_PX, 0F, toPx / SHEET_PX, 8 / SHEET_PX)
		val left = sheetSlice(0, 4)
		val middle = sheetSlice(4, 8)
		val right = sheetSlice(8, 12)
		val segmentOverlay = sheetSlice(12, 15)
	}

	private fun paintCell(gfx: GuiGraphicsExtractor, tex: Texture, x: Int, cellWidth: Int, from: Double, to: Double) {
		val reached = progress.get()
		if (cellWidth == CELL && (to < reached || from > reached)) {
			tex.draw(gfx, x, 0, CELL, 8, if (to < reached) fillColor else emptyColor)
			return
		}
		val perPixel = (to - from) / cellWidth
		var value = from
		repeat(cellWidth) { px ->
			tex.strip(px).draw(gfx, x + px, 0, 1, 8, if (value < reached) fillColor else emptyColor)
			value += perPixel
		}
	}

	override fun render(context: GuiImmediateContext) {
		val gfx = (context.renderContext as MoulConfigRenderContext).drawContext
		val span = context.width
		val sum = total.get()
		fun valueAt(px: Int) = px * sum / span
		val endCap = span - CELL
		for (start in 0 until endCap step CELL)
			paintCell(gfx, if (start == 0) left else middle, start, minOf(CELL, endCap - start), valueAt(start), valueAt(start + CELL))
		paintCell(gfx, right, endCap, CELL, valueAt(endCap), sum)
	}
}

fun Identifier.toMoulConfig() = MyResourceLocation(namespace, path)
