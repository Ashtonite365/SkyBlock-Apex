package dev.ashtonite365.apex.features.inventory.storageoverlay

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.menus.style.GuiCanvas
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.StyleAnim
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleCanvas
import dev.ashtonite365.apex.features.menus.style.StylePalette
import dev.ashtonite365.apex.features.menus.style.StyleWidgets
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.UserCloseHook
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.toShedaniel

@OptIn(NeedsComponents::class)
class StorageOverviewScreen : Screen(Component.empty()), UserCloseHook {
	companion object Markers {

		val placeholderPanes: List<Item> = listOf(Items.STAINED_GLASS_PANE.red(), Items.STAINED_GLASS_PANE.brown(), Items.DYE.gray())

		private var scrollOffset = 0
		private var lastContentHeight = 0

		private const val UNLOADED_FILL_DARKEN = 4.0
	}

	private val bar = StyleBar()

	private val openedAt by lazy { dev.ashtonite365.apex.features.menus.style.ScreenTransitions.openStartNs() }
	private fun barGeo() = StyleBar.geometry(width - 6, 4, MenuStyle.state, height)

	override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (!MenuStyle.clearOutsideNow()) super.extractBackground(context, mouseX, mouseY, delta)
	}

	private val stored: StorageData = StorageOverlay.Data.data
	var isClosing: Boolean = false

	override fun beforeUserClose() { isClosing = true }

	val scale: Float get() = StorageOverlay.TConfig.overviewScale / 100F

	private fun currentLayout(): StorageOverviewLayout {
		val s = scale
		val cfg = StorageOverlay.TConfig
		return StorageOverviewLayout((width / s).toInt(), (height / s).toInt(), cfg.columns, cfg.padding, cfg.margin, font.lineHeight)
	}

	private fun pageHeights(layout: StorageOverviewLayout) = stored.knownPages.values.map { layout.pageHeight(it.inventory?.rows) }

	private fun placePages(): List<StorageOverviewLayout.Placed> {
		val layout = currentLayout()
		val heights = pageHeights(layout)
		lastContentHeight = layout.contentHeight(heights)
		scrollOffset = layout.clampScroll(scrollOffset, lastContentHeight)
		return layout.place(heights, scrollOffset)
	}

	override fun init() { placePages() }

	override fun onClose() {
		if (!StorageOverlay.TConfig.retainScroll) scrollOffset = 0
		super.onClose()
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		if (input.key() == InputConstants.KEY_ESCAPE) beforeUserClose()
		return super.keyPressed(input)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
		val layout = currentLayout()
		scrollOffset = layout.clampScroll((scrollOffset + StorageOverlay.adjustScrollSpeed(verticalAmount)).toInt(), lastContentHeight)
		return true
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {

		if (bar.click(barGeo(), click.x(), click.y())) return true
		if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(click, doubled)
		val index = StorageOverviewLayout.pageAt(placePages(), click.x() / scale, click.y() / scale)
		val page = stored.knownPages.values.elementAtOrNull(index) ?: return super.mouseClicked(click, doubled)
		MenuPress.press {
			StorageOverlay.overviewToReturnTo = this
			page.slot.navigateTo()
		}
		return true
	}

	override fun extractRenderState(context: GuiGraphicsExtractor, rawMouseX: Int, rawMouseY: Int, delta: Float) {
		val p = MenuStyle.palette; val st = MenuStyle.state
		val el = (System.nanoTime() - openedAt) / 1_000_000
		val reveal = StyleAnim.openReveal(st, el)
		dev.ashtonite365.apex.features.menus.style.Frost.fade = dev.ashtonite365.apex.features.menus.MenuChrome.frostFade(StyleAnim.kind(st), reveal)
		if (!p.clearOutside) context.fill(0, 0, width, height, p.backdrop)
		val c = GuiCanvas(context, font)
		val s = scale
		val placed = placePages()
		val pages = stored.knownPages.values.toList()
		val hovered = StorageOverviewLayout.pageAt(placed, rawMouseX / s.toDouble(), rawMouseY / s.toDouble())
		context.pose().pushMatrix()
		context.pose().scale(s, s)
		for ((i, box) in placed.withIndex()) {
			val entry = pages.getOrNull(i) ?: continue
			if (box.y + box.height < 0 || box.y > height / s) continue
			StorageOverviewStyle.pageCard(c, p, box.x - 3, box.y - 3, StorageOverviewLayout.PAGE_WIDTH + 5, box.height + 5, i == hovered)
			context.pose().pushMatrix()
			context.pose().translate(box.x.toFloat(), box.y.toFloat())
			paintPage(context, entry, 0, 0, p)
			context.pose().popMatrix()
			StyleWidgets.cover(c, StyleAnim.kind(st), p.window, box.x - 3, box.y - 3, StorageOverviewLayout.PAGE_WIDTH + 5, box.height + 5, reveal, openedAt + i, el)
		}
		context.pose().popMatrix()

		context.nextStratum()
		val geo = barGeo()
		bar.drawButton(c, p, st, geo, rawMouseX, rawMouseY, false)
		bar.drawList(c, p, st, geo, rawMouseX, rawMouseY)
	}

	private fun paintPage(gfx: GuiGraphicsExtractor, entry: StorageData.RememberedPage, localX: Int, localY: Int, p: StylePalette) {
		gfx.text(font, stored.labelFor(entry.slot), localX, localY, p.text, true)
		val inventory = entry.inventory
		if (inventory == null) {
			val top = localY + font.lineHeight + 2
			val h = StorageOverviewLayout.UNLOADED_HEIGHT - font.lineHeight - 2
			gfx.fill(localX, top, localX + StorageOverviewLayout.PAGE_WIDTH - 1, top + h,
				DyeColor.RED.toShedaniel().darker(UNLOADED_FILL_DARKEN).color)
			val msg = "Not loaded yet"
			gfx.text(font, msg, localX + (StorageOverviewLayout.PAGE_WIDTH - font.width(msg)) / 2, top + (h - font.lineHeight) / 2, -1, true)
			return
		}
		for ((index, kept) in inventory.stacks.withIndex()) {
			val (cx, cy) = StorageOverviewLayout.cell(index, font.lineHeight)
			val x = localX + cx
			val y = localY + cy
			gfx.fill(x, y, x + StorageOverviewLayout.CELL, y + StorageOverviewLayout.CELL, StorageOverviewStyle.cellFill(p))
			val stack = kept.current()
			if (!stack.isEmpty) {
				gfx.item(stack, x + 1, y + 1)
				gfx.itemDecorations(font, stack, x + 1, y + 1)
			}
		}
	}
}

object StorageOverviewStyle {

	fun pageCard(c: StyleCanvas, p: StylePalette, x: Int, y: Int, w: Int, h: Int, hot: Boolean) {
		StyleWidgets.card(c, p, x, y, w, h, hot)
		dev.ashtonite365.apex.features.menus.style.ForgeMetal.plate(c, p, x, y, w, h)
	}

	fun cellFill(p: StylePalette): Int = p.well
}

class StorageOverviewLayout(
	val scaledWidth: Int,
	val scaledHeight: Int,
	val columns: Int,
	val padding: Int,
	val margin: Int,
	val lineHeight: Int,
) {
	data class Placed(val x: Int, val y: Int, val height: Int)

	companion object {
		const val CELL_PITCH = 19
		const val CELL = 18
		const val PAGE_WIDTH = 9 * CELL_PITCH
		const val UNLOADED_HEIGHT = 60

		fun cell(index: Int, lineHeight: Int): Pair<Int, Int> =
			(index % 9) * CELL_PITCH to (index / 9) * CELL_PITCH + lineHeight + 2

		fun pageAt(placed: List<Placed>, x: Double, y: Double): Int =
			placed.indexOfFirst { x >= it.x && x <= it.x + PAGE_WIDTH.toDouble() && y >= it.y && y <= it.y + it.height }
	}

	fun pageHeight(rows: Int?): Int = if (rows == null) UNLOADED_HEIGHT else rows * CELL_PITCH + lineHeight + 2

	private fun columnX(column: Int): Int {
		val total = columns * PAGE_WIDTH + (columns - 1) * padding
		return scaledWidth / 2 - total / 2 + column * (PAGE_WIDTH + padding)
	}

	fun place(heights: List<Int>, scroll: Int): List<Placed> {
		val out = ArrayList<Placed>(heights.size)
		var y = margin - scroll
		for (row in heights.chunked(columns.coerceAtLeast(1))) {
			row.forEachIndexed { c, h -> out += Placed(columnX(c), y, h) }
			y += row.max() + padding
		}
		return out
	}

	fun contentHeight(heights: List<Int>): Int =
		heights.chunked(columns.coerceAtLeast(1)).sumOf { it.max() + padding }

	fun maxScroll(contentHeight: Int): Int = contentHeight - scaledHeight + 2 * margin

	fun clampScroll(scroll: Int, contentHeight: Int): Int = scroll.coerceIn(0, maxScroll(contentHeight).coerceAtLeast(0))
}
