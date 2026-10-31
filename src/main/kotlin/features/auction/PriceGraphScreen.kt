/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex price history graph.
 */
package dev.ashtonite365.apex.features.auction

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.stackUnderCursor
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.tr

class PriceGraphScreen(
	val parent: Screen?,
	val itemId: ItemId,
	val icon: ItemStack,
) : Screen(tr("apex.auction.graph.title", "Price History")) {
	enum class Range(val label: String, val seconds: Long?) {
		DAY("1d", 86_400), THREE_DAYS("3d", 3 * 86_400), WEEK("7d", 7 * 86_400), ALL("All", null)
	}

	data class Column(val x: Int, val top: Int, val bottom: Int)

	class Chart(
		val points: List<PriceHistory.Point>,
		val left: Int, val right: Int, val upper: Int, val lower: Int,
	) {
		val start: Long = points.first().t
		val end: Long = maxOf(points.last().t, start + 1)
		val low: Double
		val high: Double

		init {
			val all = points.flatMap { p -> listOfNotNull(p.buy, p.sell) }
			var lo = all.min()
			var hi = all.max()
			if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
			low = lo; high = hi
		}

		fun xOf(t: Long): Int = left + ((t - start).toDouble() / (end - start) * (right - left - 1)).toInt()
		fun yOf(v: Double): Int = lower - 1 - ((v - low) / (high - low) * (lower - upper - 1)).toInt()

		fun line(pick: (PriceHistory.Point) -> Double?): List<Column> {
			val out = ArrayList<Column>()
			var last: Pair<Int, Int>? = null
			for (p in points) {
				val v = pick(p) ?: continue
				val here = xOf(p.t) to yOf(v)
				last?.let { (ax, ay) -> out += joint(ax, ay, here.first, here.second) }
				last = here
			}
			return out
		}

		fun nearest(mouseX: Int): PriceHistory.Point {
			var best = points.first()
			var gap = kotlin.math.abs(xOf(best.t) - mouseX)
			for (p in points) {
				val g = kotlin.math.abs(xOf(p.t) - mouseX)
				if (g < gap) { best = p; gap = g }
			}
			return best
		}
	}

	companion object {
		const val W = 300
		const val H = 170
		private const val RANGE_W = 24
		private const val RANGE_H = 12
		private val timeFormat = DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault())

		fun joint(ax: Int, ay: Int, bx: Int, by: Int): List<Column> {
			if (bx < ax) return emptyList()
			val span = bx - ax
			return (0..span).map { k ->
				val y = if (span == 0) by else ay + (by - ay) * k / span
				val before = if (span == 0 || k == 0) ay else ay + (by - ay) * (k - 1) / span
				Column(ax + k, minOf(y, before), maxOf(y, before) + 1)
			}
		}

		fun visible(points: List<PriceHistory.Point>, range: Range, now: Long): List<PriceHistory.Point> {
			val s = range.seconds ?: return points
			return points.filter { it.t >= now - s }
		}

		fun rangeButton(range: Range, left: Int, top: Int): Pair<Int, Int> =
			Pair(left + W - 4 - (Range.entries.size - range.ordinal) * 26, top + 4)

		fun rangeHit(mx: Double, my: Double, left: Int, top: Int): Range? = Range.entries.firstOrNull { r ->
			val (x, y) = rangeButton(r, left, top)
			mx >= x && mx < x + RANGE_W && my >= y && my < y + RANGE_H
		}

		@Subscribe
		fun onKey(event: HandledScreenKeyPressedEvent) {
			if (!event.matches(AuctionTools.TConfig.priceGraphKeybind)) return
			val stack = event.screen.stackUnderCursor ?: return
			val id = stack.view().repoItemId ?: return
			Client.screen = PriceGraphScreen(event.screen, id, stack.copy())
			event.cancel()
		}
	}

	private var samples: List<PriceHistory.Point>? = null
	private var range = Range.WEEK
	private val bazaarItem = PriceHistory.keyFor(itemId).startsWith("BZ:")

	override fun init() {
		if (samples != null) return
		val key = PriceHistory.keyFor(itemId)
		Apex.coroutineScope.launch(Dispatchers.IO) {
			val got = PriceHistory.load(key)
			Client.onClientThread { samples = got }
		}
	}

	private val boxLeft get() = (width - W) / 2
	private val boxTop get() = (height - H) / 2

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val hit = rangeHit(click.x(), click.y(), boxLeft, boxTop) ?: return super.mouseClicked(click, doubled)
		dev.ashtonite365.apex.features.menus.MenuPress.acted()
		range = hit
		return true
	}

	override fun onClose() {
		Client.screen = parent
	}

	private fun GuiGraphicsExtractor.grey(s: Any, x: Int, y: Int, centred: Boolean = false, colour: Int = 0xFFA0A0A0.toInt()) {
		when {
			s is Component && centred -> centeredText(font, s, x, y, colour)
			s is Component -> text(font, s, x, y, colour)
			centred -> centeredText(font, s.toString(), x, y, colour)
			else -> text(font, s.toString(), x, y, colour)
		}
	}

	override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(ctx, mouseX, mouseY, partialTick)
		val l = boxLeft
		val t = boxTop
		ctx.fill(l, t, l + W, t + H, 0xF0181818.toInt())
		ctx.item(icon, l + 4, t + 2)
		ctx.text(font, icon.hoverName, l + 24, t + 6, 0xFFFFFFFF.toInt())
		for (r in Range.entries) {
			val (bx, by) = rangeButton(r, l, t)
			ctx.fill(bx, by, bx + RANGE_W, by + RANGE_H, if (r == range) 0xFF606060.toInt() else 0xFF303030.toInt())
			ctx.centeredText(font, r.label, bx + 12, by + 2, 0xFFFFFFFF.toInt())
		}
		val x0 = l + 50
		val x1 = l + W - 8
		val y0 = t + 24
		val y1 = t + H - 22
		val midX = (x0 + x1) / 2
		val midY = (y0 + y1) / 2
		ctx.fill(x0, y0, x1, y1, 0xFF0C0C0C.toInt())
		val loaded = samples
		if (loaded == null) {
			ctx.grey(tr("apex.auction.graph.loading", "Loading..."), midX, midY, centred = true)
			return
		}
		val inRange = visible(loaded, range, Instant.now().epochSecond)
		if (inRange.size < 2) {
			ctx.grey(tr("apex.auction.graph.nodata", "Not enough recorded data yet"), midX, midY - 5, centred = true)
			ctx.grey(tr("apex.auction.graph.nodata.hint", "Prices are recorded while the game is running"), midX, midY + 6, centred = true, colour = 0xFF707070.toInt())
			return
		}
		val chart = Chart(inRange, x0, x1, y0, y1)
		for (c in chart.line { it.buy }) ctx.fill(c.x, c.top, c.x + 1, c.bottom, 0xFFFFAA00.toInt())
		if (bazaarItem) for (c in chart.line { it.sell }) ctx.fill(c.x, c.top, c.x + 1, c.bottom, 0xFF55FFFF.toInt())
		ctx.grey(ApexFormatters.compactNumber(chart.high), l + 4, y0)
		ctx.grey(ApexFormatters.compactNumber(chart.low), l + 4, y1 - 8)
		ctx.grey(timeFormat.format(Instant.ofEpochSecond(chart.start)), x0, y1 + 3)
		val endText = timeFormat.format(Instant.ofEpochSecond(chart.end))
		ctx.grey(endText, x1 - font.width(endText), y1 + 3)
		ctx.text(font, if (bazaarItem) "§6Instant buy  §bInstant sell" else "§6Lowest BIN", x0, y1 + 12, 0xFFFFFFFF.toInt())
		if (mouseX !in x0 until x1 || mouseY !in y0 until y1) return
		val pick = chart.nearest(mouseX)
		val cx = chart.xOf(pick.t)
		ctx.fill(cx, y0, cx + 1, y1, 0x60FFFFFF)
		val tip = mutableListOf<Component>(
			Component.literal(timeFormat.format(Instant.ofEpochSecond(pick.t))),
			Component.literal("§6" + ApexFormatters.withThousands(pick.buy, 1)),
		)
		pick.sell?.let { tip += Component.literal("§b" + ApexFormatters.withThousands(it, 1)) }
		ctx.setTooltipForNextFrame(font, tip, java.util.Optional.empty(), mouseX, mouseY)
	}

	override fun isPauseScreen(): Boolean = false
}
