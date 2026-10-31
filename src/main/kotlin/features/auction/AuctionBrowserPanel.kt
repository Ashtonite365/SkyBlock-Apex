/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): fancy auction browser panel. Idea from other mods' fancy
 * auction browsers (feature descriptions only, see CREDITS.md); original implementation,
 * no code copied. Display only: it lists and sorts the auctions on the current Hypixel page and highlights
 * the hovered one; you still click the item in the Hypixel menu yourself.
 * Drawn in the Apex menu theme (MenuTheme + the AH menu accent), full item names (wider or two-line rows,
 * tooltip on hover), placed left of the Apex AH menu (or the vanilla chest) and hidden when there is no room.
 */
package dev.ashtonite365.apex.features.auction

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import com.mojang.blaze3d.platform.InputConstants
import dev.ashtonite365.apex.features.menus.MenuFront
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.ah.AhMenuSettings
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.events.HandledScreenClickEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.mixins.accessor.AccessorHandledScreen
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.bareText

object AuctionBrowserPanel {
	@Config
	object TConfig : ManagedConfig("auction-browser", Category.AUCTION_BAZAAR) {
		val enabled by toggle("enabled") { true }
		val binOnly by toggle("bin-only") { false }
		val sort by choice("sort") { Sort.PRICE_ASC }
		val showLowestBinDiff by toggle("show-lowest-bin-diff") { true }
	}

	enum class Sort(val parser: AuctionListingParser.Sort) : net.minecraft.util.StringRepresentable {
		PRICE_ASC(AuctionListingParser.Sort.PRICE_ASC), PRICE_DESC(AuctionListingParser.Sort.PRICE_DESC),
		ENDING_SOON(AuctionListingParser.Sort.ENDING_SOON), MOST_BIDS(AuctionListingParser.Sort.MOST_BIDS);
		override fun getSerializedName() = name
	}

	private var sortOverride: Sort? = null
	private val sort get() = sortOverride ?: TConfig.sort
	private var hoveredSlot: Int? = null

	private fun isBrowser(screen: AbstractContainerScreen<*>) =
		screen.title.bareText.trim().let { it == "Auctions Browser" || it.startsWith("Auctions: ") }

	data class Row(val slot: Int, val listing: AuctionListing, val lbinDiffPct: Double?, val ref: Double? = null)

	data class SlotItem(val slot: Int, val name: String, val lore: List<String>, val id: String?, val count: Int)

	fun rows(screen: AbstractContainerScreen<*>): List<Row> {
		val items = screen.menu.slots.withIndex().filter { it.value.container !is Inventory && !it.value.item.isEmpty }.map { (i, slot) ->
			val stack = slot.item
			SlotItem(i, stack.hoverName.bareText.trim(), stack.view().loreAccordingToNbt.map { it.bareText },
				stack.view().repoItemId?.skyblockId, stack.count)
		}
		return rowsFrom(items, sort, TConfig.binOnly) { id -> referencePrice(dev.ashtonite365.apex.util.ItemId(id)) }
	}

	fun referenceSource(lbin: Double?, a1: Double?, a3: Double?, a7: Double?): String? = when {
		lbin != null && dev.ashtonite365.apex.repo.PriceSanity.isOutlier(lbin, a1 ?: a3) -> if (a1 != null) "1d avg" else "3d avg"
		lbin != null -> "LBIN"; a1 != null -> "1d avg"; a3 != null -> "3d avg"; a7 != null -> "7d avg"; else -> null
	}
	fun referenceSource(id: dev.ashtonite365.apex.util.ItemId): String? = with(HypixelStaticData) {
		referenceSource(lowestBin[id], avg1dlowestBin[id], avg3dlowestBin[id], avg7dlowestBin[id])
	}

	fun refLabel(source: String?, ref: Double?, fetchedAtMs: Long, nowMs: Long, fmt: (Double) -> String): String =
		if (source == null || ref == null) "no price data"
		else "$source ${fmt(ref)}" + (if (fetchedAtMs > 0) ", " + dev.ashtonite365.apex.repo.LiveAuctionPrices.ageText(nowMs - fetchedAtMs) + " ago" else "")

	fun clampScroll(scroll: Int, visible: Int, total: Int) = scroll.coerceIn(0, maxOf(0, total - visible))
	fun ensureVisible(scroll: Int, index: Int, visible: Int, total: Int) =
		clampScroll(if (index < scroll) index else if (index >= scroll + visible) index - visible + 1 else scroll, visible, total)

	fun scrollFor(prevKey: String?, key: String, scroll: Int) = if (prevKey == key) scroll else 0

	fun rowClick(rowSlot: Int, rowName: String, slotName: String?, send: (Int) -> Unit): Boolean {
		if (!rowEnabled(rowName, slotName)) return false
		send(rowSlot); return true
	}
	fun rowEnabled(rowName: String, slotName: String?) = slotName != null && slotName.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim() == rowName.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim()

	private var rowRects: List<Pair<IntArray, Row>> = emptyList()

	private fun slotName(screen: AbstractContainerScreen<*>, slot: Int): String? =
		screen.menu.slots.getOrNull(slot)?.item?.takeIf { !it.isEmpty }?.hoverName?.bareText

	private fun clickRow(screen: AbstractContainerScreen<*>, row: Row): Boolean = rowClick(row.slot, row.listing.itemName, slotName(screen, row.slot)) { slot ->
		val view = (screen.customGui as? MenuFront)?.session?.view as? dev.ashtonite365.apex.features.menus.support.ApexViewBase<*>
		if (view != null) view.clickSlotFromPanel(slot)
		else if (Client.player?.containerMenu === screen.menu) screen.menu.slots.getOrNull(slot)?.let { dev.ashtonite365.apex.features.menus.MenuClicks.click(screen.menu, it, 0) }
	}

	private var scroll = 0
	private var pageKey: String? = null
	private var listRect: IntArray? = null
	private var visibleRows = 1
	private var totalRows = 0

	fun referencePrice(id: dev.ashtonite365.apex.util.ItemId): Double? =
		HypixelStaticData.lowestBin[id] ?: HypixelStaticData.avg1dlowestBin[id] ?: HypixelStaticData.avg3dlowestBin[id] ?: HypixelStaticData.avg7dlowestBin[id]

	fun rowsFrom(items: List<SlotItem>, sort: Sort, binFirst: Boolean, reference: (String) -> Double?): List<Row> {
		val rows = items.mapNotNull { it ->
			val listing = AuctionListingParser.parse(it.name, it.lore) ?: return@mapNotNull null
			val ref = it.id?.let(reference)?.times(it.count)
			val diff = if (ref != null && ref > 0 && listing.price != null) (listing.price / ref - 1) * 100 else null
			Row(it.slot, listing, diff, ref)
		}
		val order = AuctionListingParser.sort(rows.map { it.listing }, sort.parser).map { l -> rows.first { it.listing === l } }
		return if (binFirst) order.sortedByDescending { it.listing.isBin } else order
	}

	fun diffText(pct: Double?, show: Boolean = true): String = when {
		!show -> ""
		pct == null -> " §7?"
		else -> (if (pct <= 0) " §a" else " §c") + "%+.0f%%".format(pct)
	}

	private const val CHIP_H = 16
	private const val ROW_H = 11
	private fun sortLabel() = "Sort: ${sort.name.lowercase().replace('_', ' ')}"

	private var chipRect: IntArray? = null
	private var drawnScreen: AbstractContainerScreen<*>? = null

	data class Layout(val w: Int, val twoLine: Boolean, val rowH: Int)
	fun layout(rows: List<Pair<String, String>>, avail: Int, measure: (String) -> Int, chipText: String = "", minW: Int = 120, pad: Int = 6): Layout? {
		if (avail < minW) return null
		val one = (rows.maxOfOrNull { measure(it.first) + 8 + measure(it.second) } ?: 0) + 2 * pad
		val chip = measure(chipText) + 2 * pad + 12
		if (one <= avail) return Layout(maxOf(one, chip, minW), false, ROW_H)
		val two = (rows.maxOfOrNull { maxOf(measure(it.first), measure(it.second) + 8) } ?: 0) + 2 * pad
		return Layout(minOf(avail, maxOf(two, chip, minW)), true, 2 * ROW_H)
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
			if (screen !is AbstractContainerScreen<*> || !TConfig.enabled || !isBrowser(screen)) return@AfterInit

			ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click -> dev.ashtonite365.apex.features.menus.MenuPress.press {
				val r = chipRect
				if (drawnScreen === screen && r != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT &&
					MenuTheme.inside(click.x().toInt(), click.y().toInt(), r[0], r[1], r[2], r[3])) {
					dev.ashtonite365.apex.features.menus.MenuPress.acted(); sortOverride = Sort.entries[(sort.ordinal + 1) % Sort.entries.size]; false
				} else if (drawnScreen === screen && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
					val hit = rowRects.lastOrNull { (q, _) -> MenuTheme.inside(click.x().toInt(), click.y().toInt(), q[0], q[1], q[2], q[3]) }
					if (hit != null) { dev.ashtonite365.apex.features.menus.MenuPress.acted(); clickRow(screen, hit.second); false } else true
				} else true
			} })
			ScreenMouseEvents.allowMouseScroll(screen).register(ScreenMouseEvents.AllowMouseScroll { _, mx, my, _, v ->
				val r = listRect
				if (drawnScreen === screen && r != null && MenuTheme.inside(mx.toInt(), my.toInt(), r[0], r[1], r[2], r[3])) {
					scroll = clampScroll(scroll - v.toInt().let { if (it == 0) (if (v > 0) 1 else -1) else it } * 3, visibleRows, totalRows); false
				} else true
			})
		})
	}

	@Subscribe
	fun onRender(event: ScreenRenderPostEvent) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		hoveredSlot = null; chipRect = null; AhHoverLink.panelSlot = null
		if (!TConfig.enabled || !isBrowser(screen)) return
		val ctx = event.drawContext
		val font = Client.font
		val pal = AhMenuSettings.customisation.palette

		val front = screen.customGui as? MenuFront
		val anchorX = front?.session?.view?.x ?: (screen as AccessorHandledScreen).x_Apex
		val top = front?.session?.view?.y ?: (screen as AccessorHandledScreen).y_Apex
		val rows = rows(screen)
		val refSources = rows.associate { r -> r.slot to screen.menu.slots.getOrNull(r.slot)?.item?.view()?.repoItemId?.skyblockId
			?.let { referenceSource(dev.ashtonite365.apex.util.ItemId(it)) } }
		val key = screen.title.string + "|" + rows.joinToString(",") { it.slot.toString() + it.listing.itemName }
		scroll = scrollFor(pageKey, key, scroll); pageKey = key

		val acc = screen as AccessorHandledScreen
		val vanillaSlot = if (front != null) null else rows.firstOrNull { r -> screen.menu.slots.getOrNull(r.slot)?.let { sl ->
			MenuTheme.inside(event.mouseX, event.mouseY, acc.x_Apex + sl.x, acc.y_Apex + sl.y, 16, 16) } == true }?.slot

		val gridSlot = AhHoverLink.rowFor(rows.map { it.slot }, if (front != null) AhHoverLink.gridSlot else null, vanillaSlot)
		val texts = rows.map { r ->
			val price = r.listing.price?.let { ApexFormatters.compactNumber(it) } ?: "?"
			val diff = diffText(r.lbinDiffPct, TConfig.showLowestBinDiff)
			val tag = if (r.listing.isBin) "§bBIN " else "§e${r.listing.bids}b "
			val ends = r.listing.endsIn?.let { " §7" + ApexFormatters.timespanText(it) } ?: ""
			val ref = if (TConfig.showLowestBinDiff) r.ref?.let { " §8(" + (refSources[r.slot] ?: "ref") + " " + ApexFormatters.compactNumber(it) + ")" } ?: "" else ""
			"$tag§f${r.listing.itemName}" to "§6$price$diff$ref$ends"
		}
		val avail = anchorX - 6 - 2
		val lay = layout(texts, avail, font::width, sortLabel()) ?: return
		val x = anchorX - 6 - lay.w
		drawnScreen = screen

		val chipHover = MenuTheme.inside(event.mouseX, event.mouseY, x, top, lay.w, CHIP_H)
		MenuTheme.button(ctx, font, x, top, lay.w, CHIP_H, sortLabel(), MenuTheme.state(chipHover, active = true), palette = pal)
		chipRect = intArrayOf(x, top, lay.w, CHIP_H)
		val py = top + CHIP_H + 4
		val ageLine = "§7Ref: " + (if (HypixelStaticData.lowestBinFetchedAtMs > 0) "prices " + dev.ashtonite365.apex.repo.LiveAuctionPrices.ageText(System.currentTimeMillis() - HypixelStaticData.lowestBinFetchedAtMs) + " ago" else "no price data")
		val maxRows = ((screen.height - py - 8 - ROW_H) / lay.rowH).coerceAtLeast(1)
		visibleRows = minOf(maxRows, texts.size).coerceAtLeast(1); totalRows = texts.size
		if (gridSlot >= 0) scroll = ensureVisible(scroll, gridSlot, visibleRows, totalRows)
		scroll = clampScroll(scroll, visibleRows, totalRows)
		val shown = texts.drop(scroll).take(visibleRows)
		val listH = shown.size * lay.rowH
		val ph = listH + 8 + ROW_H
		MenuTheme.panel(ctx, x, py, lay.w, ph, pal)
		ctx.text(font, ageLine, x + 6, py + 4, pal.textDim)
		val ly = py + 4 + ROW_H
		listRect = intArrayOf(x, py, lay.w, ph)
		var hoverIndex: Int? = null
		val rects = mutableListOf<Pair<IntArray, Row>>()
		ctx.enableScissor(x + 1, ly, x + lay.w - 1, ly + listH)
		shown.forEachIndexed { j, (left, right) ->
			val i = j + scroll
			val ry = ly + j * lay.rowH
			val hov = MenuTheme.inside(event.mouseX, event.mouseY, x + 1, ry, lay.w - 2, lay.rowH)
			if (hov) hoverIndex = i
			val other = dev.ashtonite365.apex.features.museum.MuseumAhTarget.blocked(screen.menu.slots.getOrNull(rows[i].slot)?.item?.view()?.repoItemId?.skyblockId)
			val enabled = rowEnabled(rows[i].listing.itemName, slotName(screen, rows[i].slot)) && !other
			if (enabled) rects += intArrayOf(x + 1, ry, lay.w - 2, lay.rowH) to rows[i]
			if (hov || i == gridSlot) ctx.fill(x + 1, ry, x + lay.w - 1, ry + lay.rowH, pal.fillHover)
			ctx.text(font, if (enabled) left else "§8" + left.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "") + if (other) " §7(other item)" else " §7(not in window)", x + 6, ry + 1, if (enabled) pal.text else pal.textDim)
			val rx = x + lay.w - 6 - font.width(right)
			ctx.text(font, right, rx, ry + 1 + if (lay.twoLine) ROW_H else 0, pal.text)
		}
		ctx.disableScissor()
		rowRects = rects
		MenuTheme.scrollbar(ctx, x + lay.w - 3, ly, listH, visibleRows, totalRows, scroll)
		hoverIndex?.let { i ->
			hoveredSlot = rows[i].slot; AhHoverLink.panelSlot = rows[i].slot
			screen.menu.slots.getOrNull(rows[i].slot)?.item?.takeIf { !it.isEmpty }?.let { ctx.setTooltipForNextFrame(font, it, event.mouseX, event.mouseY) }
		}
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.Before) {
		if (hoveredSlot == null || event.slot.container is Inventory) return
		val screen = Client.containerScreen ?: return
		if (screen.menu.slots.getOrNull(hoveredSlot!!) !== event.slot) return
		event.context.fill(event.slot.x - 1, event.slot.y - 1, event.slot.x + 17, event.slot.y + 17, 0x80FFFF00.toInt())
	}
}
