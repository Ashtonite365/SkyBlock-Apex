// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.auction

import dev.ashtonite365.apex.repo.itemsSafe
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignTextSlot
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemCache.asItemStack
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.catalogItemId
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.bazaarProduct
import dev.ashtonite365.apex.util.stripSectionCodes
import dev.ashtonite365.apex.util.tr

class SignSearchScreen(
	val kind: AuctionTools.SearchKind,

	val signPos: BlockPos?,
	val signSlot: SignTextSlot?,
) : Screen(tr("apex.auction.search.title", "Search")) {

	data class Entry(val label: String, val id: ItemId?, val price: Double?)

	class Quote(val price: Double?, val weight: Double)

	companion object {
		private var lastSearch = ""
		const val PANEL_WIDTH = 220
		const val ROW_HEIGHT = 18
		const val VISIBLE_ROWS = 10
		private const val MAX_RESULTS = 100

		fun rank(query: String, items: Iterable<Pair<ItemId, String>>, quote: (ItemId, String) -> Quote?): List<Entry> {
			val words = SearchMatcher.words(query.trim().lowercase())
			val taken = HashSet<String>()
			val scored = ArrayList<Pair<Entry, Double>>()
			for ((id, display) in items) {
				val name = display.stripSectionCodes().trim()
				val key = name.lowercase()
				if (!SearchMatcher.matches(key, words)) continue
				val q = quote(id, name) ?: continue
				if (taken.add(key)) scored += Entry(name, id, q.price) to q.weight
			}
			return scored.sortedByDescending { it.second }.take(MAX_RESULTS).map { it.first }
		}

		fun bazaarQuote(id: ItemId): Quote? {
			val status = HypixelStaticData.bazaarData[id.bazaarProduct]?.quickStatus ?: return null
			return Quote(status.buyPrice, (status.buyMovingWeek + status.sellMovingWeek).toDouble())
		}

		fun auctionQuote(hasBazaar: Boolean, lowestBin: Double?, name: String): Quote? {
			if (hasBazaar) return null
			val shortness = 1.0 / (1 + name.length)
			return Quote(lowestBin, if (lowestBin != null) shortness + 1 else shortness)
		}

		fun rowAt(mouseX: Double, mouseY: Double, left: Int, listTop: Int, scroll: Int, count: Int): Int? {
			if (mouseX < left || mouseX > left + PANEL_WIDTH || mouseY < listTop) return null
			val row = ((mouseY - listTop) / ROW_HEIGHT).toInt()
			if (row >= VISIBLE_ROWS) return null
			return (row + scroll).takeIf { it in 0 until count }
		}

		fun scrolled(scroll: Int, wheel: Double, count: Int): Int = (scroll - wheel.toInt()).coerceIn(0, maxOf(0, count - VISIBLE_ROWS))
	}

	private lateinit var input: EditBox
	private var entries: List<Entry> = listOf()
	private var scroll by dev.ashtonite365.apex.util.ScrollMemory.int { "sign-search" }
	private var submitted = false
	private val icons = mutableMapOf<ItemId, ItemStack>()

	private val left get() = (width - PANEL_WIDTH) / 2
	private val top get() = height / 2 - (VISIBLE_ROWS * ROW_HEIGHT + 30) / 2
	private val listTop get() = top + 26

	override fun init() {
		val text = when {
			::input.isInitialized -> input.value
			AuctionTools.TConfig.keepPreviousSearch -> lastSearch
			else -> ""
		}
		input = EditBox(font, left + 4, top + 4, PANEL_WIDTH - 8, 18, title)
		input.setMaxLength(30)
		input.value = text
		input.setResponder { refresh() }
		addRenderableWidget(input)
		setInitialFocus(input)
		refresh()
	}

	private fun refresh() {
		val text = input.value
		if (text.isNotBlank()) scroll = 0
		if (text.trim().isEmpty()) {
			entries = AuctionTools.history(kind).map { Entry(it, null, null) }
			return
		}
		val items = RepoManager.coreRepo.items.itemsSafe.values.map { it.catalogItemId to it.displayName }
		entries = rank(text, items) { id, name ->
			when (kind) {
				AuctionTools.SearchKind.BAZAAR -> bazaarQuote(id)
				AuctionTools.SearchKind.AUCTION -> auctionQuote(HypixelStaticData.hasBazaarStock(id.bazaarProduct), HypixelStaticData.lowestBin[id], name)
			}
		}
	}

	private fun submit(text: String) {
		if (submitted) return
		submitted = true
		AuctionTools.rememberSearch(kind, text)
		lastSearch = text
		send(text.trim())
		Client.screen = null
	}

	private fun send(query: String) {
		if (signPos != null && signSlot != null) AuctionTools.sendSign(signPos, signSlot, query)
		else AuctionTools.sendSearchCommand(kind, query)
	}

	override fun onClose() = submit("")

	override fun removed() {
		if (!submitted) {
			submitted = true
			if (signPos != null && signSlot != null) AuctionTools.sendSign(signPos, signSlot, "")
		}
		super.removed()
	}

	override fun keyPressed(input: KeyEvent): Boolean = when (input.input()) {
		InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> { submit(this.input.value); true }
		InputConstants.KEY_TAB -> { entries.firstOrNull()?.let { this.input.value = it.label }; true }
		else -> super.keyPressed(input)
	}

	private fun hoveredRow(mx: Double, my: Double) = rowAt(mx, my, left, listTop, scroll, entries.size)

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val index = hoveredRow(click.x(), click.y()) ?: return super.mouseClicked(click, doubled)
		dev.ashtonite365.apex.features.menus.MenuPress.acted()
		submit(entries[index].label)
		return true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		scroll = scrolled(scroll, scrollY, entries.size)
		return true
	}

	@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
	override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		super.extractRenderState(context, mouseX, mouseY, partialTick)
		context.fill(left, listTop, left + PANEL_WIDTH, listTop + VISIBLE_ROWS * ROW_HEIGHT, 0xC0101010.toInt())
		if (entries.isEmpty()) {
			val hint = if (input.value.isBlank()) tr("apex.auction.search.nohistory", "Type to search")
			else tr("apex.auction.search.noresults", "No matching items")
			context.text(font, hint, left + 6, listTop + 5, 0xFFA0A0A0.toInt())
		}
		val hovered = hoveredRow(mouseX.toDouble(), mouseY.toDouble())
		val shown = if (scroll < 0) emptyList() else entries.drop(scroll).take(VISIBLE_ROWS)
		for ((row, entry) in shown.withIndex()) {
			val y = listTop + row * ROW_HEIGHT
			if (row + scroll == hovered) context.fill(left, y, left + PANEL_WIDTH, y + ROW_HEIGHT, 0x40FFFFFF)
			val id = entry.id
			val textX = if (id != null) {
				context.item(icons.getOrPut(id) { RepoManager.getCoreItem(id).asItemStack(id).rebuildLive() }, left + 2, y + 1)
				left + 22
			} else {
				context.text(font, "⟲", left + 6, y + 5, 0xFF808080.toInt())
				left + 18
			}
			context.text(font, entry.label, textX, y + 5, 0xFFFFFFFF.toInt())
			entry.price?.let { ApexFormatters.compactNumber(it) }?.let { context.text(font, it, left + PANEL_WIDTH - 4 - font.width(it), y + 5, 0xFFFFAA00.toInt()) }
		}
		context.text(font, Component.literal(if (kind == AuctionTools.SearchKind.AUCTION) "Auction House" else "Bazaar"), left, top - 10, 0xFFFFFFFF.toInt())
	}

	override fun isPauseScreen(): Boolean = false
}
