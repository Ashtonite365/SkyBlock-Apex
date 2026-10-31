/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex museum helper: remembers per profile which museum entries are donated (read from the museum pages
 * as they are opened), tints vacant slots, and lists the undonated entries with their current AH price in a side
 * panel. "Buy" only types /ahs for the row; the purchase is always the player's own click on Hypixel's menu.
 */
package dev.ashtonite365.apex.features.museum

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import me.shedaniel.math.Rectangle
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.commands.onLiteral
import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HandledScreenPushREIEvent
import dev.ashtonite365.apex.events.ScreenRenderPostEvent
import dev.ashtonite365.apex.events.SlotRenderEvents
import dev.ashtonite365.apex.features.menus.MenuPress
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.repo.ExpensiveItemCacheApi
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.repo.ItemNameLookup
import dev.ashtonite365.apex.repo.LiveAuctionPrices
import dev.ashtonite365.apex.repo.RepoConstants
import dev.ashtonite365.apex.repo.SBItemStack
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.accessors.getProperRectangle
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.bareText

object MuseumHelper {
	val identifier: String
		get() = "museum-helper"

	enum class Sort(val id: String) : StringRepresentable {
		CHEAPEST("cheapest"), MOST_EXPENSIVE("most-expensive");

		override fun getSerializedName() = id
	}

	enum class Side(val id: String) : StringRepresentable {
		LEFT("left"), RIGHT("right");

		override fun getSerializedName() = id
	}

	@Config
	object TConfig : ManagedConfig(category = Category.MUSEUM, name = identifier) {
		val highlightVacant by toggle("highlight-vacant") { true }
		val cheapestOverlay by toggle("cheapest-overlay") { true }
		val overlayLines by integer("overlay-lines", 5, 25) { 10 }
		var sort by choice("sort") { Sort.CHEAPEST }
		val panelSide by choice("panel-side") { Side.LEFT }
		val allCategories by toggle("all-categories") { true }
		val renderRealItem by toggle("render-real-item") { true }
	}

	@Serializable
	data class Entry(val ids: List<String> = listOf(), var donated: Boolean = false)

	@Serializable
	data class Count(val donated: Int, val total: Int? = null, val seenAtMs: Long = 0L)

	@Serializable
	data class Data(val categories: MutableMap<String, MutableMap<String, Entry>> = mutableMapOf(),

	                val counts: MutableMap<String, Count> = mutableMapOf())

	@Config
	object DConfig : ProfileSpecificDataHolder<Data>(serializer(), "museum-donations", ::Data)

	private val FORMAT_CODE = dev.ashtonite365.apex.util.SharedRegex.SECTION
	private fun plain(s: String) = s.replace(FORMAT_CODE, "").trim()

	object MuseumCounts {
		data class Shown(val donated: Int, val total: Int?, val cached: Boolean) {
			val fraction: Double? get() {
				val t = total ?: return null
				if (t <= 0) return null
				return (donated.toDouble() / t).coerceIn(0.0, 1.0)
			}
		}

		var dataSource: () -> Data? = { runCatching { DConfig.data }.getOrNull() }
		var onChanged: () -> Unit = { runCatching { DConfig.markDirty() } }

		fun key(name: String) = plain(name).lowercase()

		fun record(d: Data, name: String, donated: Int, total: Int?, now: Long): Boolean {
			val k = key(name)
			val before = d.counts[k]
			if (before?.donated == donated && before.total == total) return false
			d.counts[k] = Count(donated, total, now)
			return true
		}

		fun recordModel(m: dev.ashtonite365.apex.features.menus.museum.MuseumModel, now: Long) {
			val d = dataSource() ?: return
			val writes = mutableListOf<Boolean>()
			for (c in m.categories) c.donated?.let { writes += record(d, c.name, it, c.total, now) }
			m.stats?.let { st -> st.donated?.let { writes += record(d, "museum", it, st.total, now) } }
			val cat = m.category
			val n = m.donated ?: m.countOnly
			if (cat != null && n != null) writes += record(d, cat, n, m.total, now)
			if (true in writes) onChanged()
		}

		fun shown(name: String, liveDonated: Int?, liveTotal: Int?, d: Data? = dataSource()): Shown? =
			if (liveDonated != null) Shown(liveDonated, liveTotal, false)
			else d?.counts?.get(key(name))?.let { Shown(it.donated, it.total, true) }

		fun label(s: Shown?): String {
			if (s == null) return "§8?/?"
			val t = s.total ?: return "§a${s.donated}"
			return "§a${s.donated}§7/§f$t"
		}
	}

	val CATEGORIES = listOf("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering", "Hunting", "Special",

	                        "Weapons", "Armor Sets", "Rarities", "Special Items")

	private val PAGE_COUNTER = Regex("\\s*\\(\\d+/\\d+\\)$")

	fun categoryOf(title: String): String? {
		if (!title.contains('➜') || !title.contains("Museum", ignoreCase = true)) return null
		return title.substringAfterLast('➜').replace(PAGE_COUNTER, "").trim().ifEmpty { null }
	}

	fun isOverviewTitle(title: String) = !title.contains('➜') && title.trim().endsWith("Museum")

	fun category(screen: AbstractContainerScreen<*>): String? = categoryOf(screen.title.bareText)

	fun isOverview(screen: AbstractContainerScreen<*>?): Boolean = screen != null && isOverviewTitle(screen.title.bareText)

	private val NAME_TAILS = listOf(" armor", " equipment", " set", " outfit", " suit")

	fun armorKey(displayName: String): String {
		var k = displayName.replace("'s", "").replace("- ", "").lowercase().trim()
		for (tail in NAME_TAILS) k = k.removeSuffix(tail)
		return k.replace(' ', '_').uppercase()
	}

	fun setPieces(museum: com.google.gson.JsonObject, displayName: String): List<ItemId> {
		val key = museum.getAsJsonObject("set_exceptions")?.get(armorKey(displayName))?.asString ?: armorKey(displayName)
		val sets = museum.getAsJsonObject("sets_to_items") ?: return emptyList()
		val arr = (sets[key] ?: sets[key + "_ARMOR"])?.asJsonArray ?: return emptyList()
		return arr.map { ItemId(it.asString) }
	}

	fun armorSetIds(displayName: String): List<ItemId> =
		RepoConstants.get("museum")?.let { setPieces(it, displayName) } ?: emptyList()

	private val SET_WORD = Regex("(?i)\\b(armor|set|equipment|outfit|suit)$")

	fun idsFor(name: String, category: String): List<ItemId> {
		val looksLikeSet = category.endsWith("Armor Sets") || SET_WORD.containsMatchIn(name)
		if (looksLikeSet) armorSetIds(name).let { if (it.isNotEmpty()) return it }
		val single = ItemNameLookup.guessItemByName(name, false)
		return if (single != null) listOf(single) else armorSetIds(name)
	}

	private val realItems = HashMap<String, ItemStack?>()

	@OptIn(ExpensiveItemCacheApi::class, dev.ashtonite365.apex.util.mc.NeedsComponents::class)
	fun realItem(dye: ItemStack, category: String): ItemStack? {
		val name = dye.hoverName.bareText.trim()
		return realItems.getOrPut("$name|$category") {
			val id = idsFor(name, category).firstOrNull() ?: return@getOrPut null
			runCatching { SBItemStack(id, 1).asImmutableItemStack().current() }.getOrNull()
		}
	}

	private val FILLERS by lazy { listOf(Items.STAINED_GLASS_PANE.pick(DyeColor.BLACK), Items.STAINED_GLASS_PANE.pick(DyeColor.GRAY), Items.ARROW, Items.BARRIER) }

	fun status(stack: ItemStack): Boolean? {
		if (stack.isEmpty || FILLERS.any { stack.`is`(it) }) return null
		val notDonated = stack.view().loreAccordingToNbt.any {
			val t = it.bareText
			t.contains("Not Donated", true) || t.contains("Missing", true)
		}
		return !(notDonated || stack.`is`(Items.DYE.pick(DyeColor.GRAY)))
	}

	private fun inEntryArea(index: Int) = index in 9..44 && index % 9 != 0 && index % 9 != 8

	fun recordPage(into: MutableMap<String, Entry>, category: String, slots: List<ItemStack?>, ids: (String, String) -> List<String>): Int {
		var read = 0
		slots.forEachIndexed { index, stack ->
			if (stack == null || !inEntryArea(index)) return@forEachIndexed
			val donated = status(stack) ?: return@forEachIndexed
			read++
			val name = stack.hoverName.bareText.trim()
			val known = into[name]
			if (known?.donated == donated) return@forEachIndexed
			into[name] = Entry(known?.ids?.ifEmpty { null } ?: ids(name, category), donated)
		}
		return read
	}

	fun record(screen: AbstractContainerScreen<*>): Int {
		val category = category(screen) ?: return 0
		val page = DConfig.data.categories.getOrPut(category) { mutableMapOf() }
		val inv = Client.player?.inventory
		val stacks = screen.menu.slots.map { if (it.container === inv) null else it.item }
		val read = recordPage(page, category, stacks) { n, c -> idsFor(n, c).map { it.skyblockId } }
		if (read > 0) DConfig.markDirty()
		return read
	}

	@Subscribe
	fun onInventory(event: ChestInventoryUpdateEvent) {
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		record(screen)
	}

	enum class PriceKind(val label: String) { BIN("BIN"), BID("Bid") }

	data class AhQuote(val price: Double, val kind: PriceKind)

	enum class PieceStatus(val label: String) { LIVE("live"), LOW_CONFIDENCE("low-confidence"), STALE("stale"), BID_ONLY("bid-only"), MISSING("missing") }

	data class Piece(val id: ItemId, val status: PieceStatus, val binPrice: Double?, val bid: Double? = null)

	private fun Double?.positive(): Double? = this?.takeIf { it > 0 }

	private inline fun sumAll(ids: List<ItemId>, f: (ItemId) -> Double?): Double? {
		var total = 0.0
		for (id in ids) total += f(id).positive() ?: return null
		return total
	}

	data class Missing(val name: String, val ids: List<ItemId>, val category: String, val lowerTiers: Int,
	                   private val binOf: (ItemId) -> Double? = ::currentBin,
	                   private val bidOf: (ItemId) -> Double? = ::currentBid,
	                   private val staleOf: (ItemId) -> Double? = ::staleBin,
	                   private val bazaarOf: (ItemId) -> Boolean = ::isBazaarItem,
	                   private val lowConfOf: (ItemId) -> Double? = ::lowConfidenceBin,
	                   private val noLiveOf: (ItemId) -> Boolean = ::noLiveListing,
	                   private val nameOf: (ItemId) -> String = ::itemName) {
		private val single: ItemId? get() = ids.singleOrNull()
		val isSet: Boolean get() = ids.size > 1

		val pieces: List<Piece> by lazy { ids.map { piece(it, binOf, bidOf, staleOf, bazaarOf, lowConfOf, noLiveOf) } }

		val quote: AhQuote? by lazy { ahQuote(ids, binOf) { null } }
		val price: Double? get() = quote?.price
		val estimate: Boolean get() = quote == null

		val staleQuote: Double? by lazy { if (quote == null && single != null) sumAll(ids, staleOf) else null }

		val lowConfQuote: Double? by lazy { val id = single; if (quote == null && staleQuote == null && id != null) lowConfOf(id).positive() else null }

		val noLive: Boolean by lazy { val id = single; quote == null && staleQuote == null && lowConfQuote == null && id != null && noLiveOf(id) }

		val bidOnly: Boolean by lazy {
			val id = single
			id != null && quote == null && staleQuote == null && lowConfQuote == null && !noLive && !bazaarOf(id) && !noLiveOf(id)
		}
		val bidQuote: Double? by lazy { if (bidOnly) sumAll(ids, bidOf) else null }

		val setEstimate: Double? by lazy {
			if (!isSet || quote != null) null else pieces.mapNotNull { it.binPrice }.ifEmpty { null }?.sum()
		}
		val missingPieces: Int get() = if (!isSet) 0 else pieces.count { it.binPrice == null }

		val buyPiece: Piece? get() = pieces.firstOrNull { it.binPrice == null } ?: pieces.minByOrNull { it.binPrice!! }
		fun buyName(): String = if (!isSet) name else buyPiece?.let { nameOf(it.id) } ?: name

		val estimateValue: Double? get() = staleQuote ?: setEstimate ?: lowConfQuote ?: bidQuote
	}

	fun piece(id: ItemId, binOf: (ItemId) -> Double?, bidOf: (ItemId) -> Double?, staleOf: (ItemId) -> Double?,
	          bazaarOf: (ItemId) -> Boolean, lowConfOf: (ItemId) -> Double?, noLiveOf: (ItemId) -> Boolean): Piece {
		val tiers = listOf(PieceStatus.LIVE to binOf, PieceStatus.LOW_CONFIDENCE to lowConfOf, PieceStatus.STALE to staleOf)
		for ((st, f) in tiers) f(id).positive()?.let { return Piece(id, st, it) }
		return if (bazaarOf(id) || noLiveOf(id)) Piece(id, PieceStatus.MISSING, null)
		else Piece(id, PieceStatus.BID_ONLY, null, bidOf(id).positive())
	}

	fun ahQuote(ids: List<ItemId>, binOf: (ItemId) -> Double?, bidOf: (ItemId) -> Double?): AhQuote? {
		if (ids.isEmpty()) return null
		var total = 0.0
		var kind = PriceKind.BIN
		for (id in ids) {
			val bin = binOf(id).positive()
			if (bin != null) total += bin
			else { total += bidOf(id).positive() ?: return null; kind = PriceKind.BID }
		}
		return AhQuote(total, kind)
	}

	fun rowTooltip(row: Missing, shown: String, fmt: (Double) -> String, nameOf: (ItemId) -> String = { it.skyblockId }): List<String> {
		val full = plain(row.name)
		if (!row.isSet) return listOfNotNull(CheapestList.tooltip(full, shown))
		val lines = mutableListOf(full)
		for (p in row.pieces) {
			val value = when {
				p.binPrice != null -> fmt(p.binPrice)
				p.bid != null -> "~${fmt(p.bid)} (bid)"
				else -> "—"
			}
			lines += "§7${nameOf(p.id)}: §f$value §8(${p.status.label})"
		}
		return lines
	}

	fun itemName(id: ItemId): String =
		runCatching { dev.ashtonite365.apex.repo.RepoManager.getCoreItem(id)?.displayName?.let { net.minecraft.util.StringUtil.stripColor(it) } }.getOrNull() ?: id.skyblockId

	fun lowConfidenceBin(id: ItemId): Double? = LiveAuctionPrices.current.lowConfidenceBin(id.skyblockId)
	fun noLiveListing(id: ItemId): Boolean = LiveAuctionPrices.current.noLiveListing(id.skyblockId)

	fun isBazaarItem(id: ItemId): Boolean = HypixelStaticData.bazaarData.containsKey(ItemId.BazaarProduct(id.skyblockId))

	fun staleBin(id: ItemId): Double? = LiveAuctionPrices.current.staleBin(id.skyblockId, System.currentTimeMillis())

	fun currentBin(id: ItemId): Double? {
		val now = System.currentTimeMillis()
		return liveBin(LiveAuctionPrices.current.bin(id.skyblockId, now), AhSeenPrices.bin(id.skyblockId, now))
	}

	fun liveBin(feed: Double?, seen: Double?): Double? {
		val a = feed.positive()
		val b = seen.positive()
		return if (a == null) b else if (b == null) a else minOf(a, b)
	}

	@Suppress("UNUSED_PARAMETER")
	fun currentBid(id: ItemId): Double? = null

	private fun approx(fmt: (Double) -> String, v: Double) = "§8~" + fmt(v)

	fun priceText(row: Missing, fmt: (Double) -> String): String {
		row.quote?.let { return "§6${fmt(it.price)} §7${it.kind.label}" }
		row.staleQuote?.let { return approx(fmt, it) + " estimate" }
		if (row.isSet) {
			val est = row.setEstimate ?: return "§8no live listing"
			val gaps = row.missingPieces
			return if (gaps == 0) approx(fmt, est) + " estimate"
			else approx(fmt, est) + "+ ($gaps ${if (gaps == 1) "piece" else "pieces"} missing)"
		}
		row.lowConfQuote?.let { return approx(fmt, it) + " estimate (1 listing)" }
		return when {
			row.noLive -> "§8no live listing"
			row.bidOnly -> row.bidQuote?.let { "§7~${fmt(it)} (bid)" } ?: "§7bid only"
			else -> "§8no live price"
		}
	}

	fun listedOnly(rows: List<Missing>, sort: Sort): List<Missing> {
		val (live, rest) = rows.partition { it.price != null }
		val byPrice = compareBy<Missing> { it.price }
		val ordered = live.sortedWith(if (sort == Sort.CHEAPEST) byPrice else byPrice.reversed())
		val estimates = rest.filter { it.isSet || it.staleQuote != null || it.lowConfQuote != null || it.noLive || it.bidOnly }
		return ordered + estimates.sortedWith(compareBy(nullsLast()) { it.estimateValue })
	}

	private fun children(): Map<String, String> =
		RepoConstants.get("museum")?.getAsJsonObject("children")?.entrySet()?.associate { it.key to it.value.asString } ?: emptyMap()

	private inline fun walkChain(start: String?, child: Map<String, String>, keepGoing: (String) -> Boolean) {
		var cur = start
		var steps = 0
		while (cur != null && steps++ < 20 && keepGoing(cur)) cur = child[cur]
	}

	fun coveredIds(data: Data, child: Map<String, String>): Set<String> {
		val covered = LinkedHashSet<String>()
		for (entry in data.categories.values.asSequence().flatMap { it.values.asSequence() })
			if (entry.donated) for (id in entry.ids) walkChain(id, child) { covered.add(it) }
		return covered
	}

	fun coveredIds(data: Data): Set<String> = coveredIds(data, children())

	fun missingFrom(data: Data, child: Map<String, String>, onlyCategory: String?, sort: Sort,
	                row: (String, List<ItemId>, String, Int) -> Missing = { n, i, c, l -> Missing(n, i, c, l) }): List<Missing> {
		val covered = coveredIds(data, child)
		val open = data.categories.values.flatMap { it.values }.filterNot { it.donated }.flatMap { it.ids }.toSet()
		val rows = ArrayList<Missing>()
		for ((cat, entries) in data.categories) {
			if (onlyCategory != null && cat != onlyCategory) continue
			for ((name, e) in entries) {
				if (e.donated) continue
				if (e.ids.isNotEmpty() && covered.containsAll(e.ids)) continue
				var below = 0
				var cur = e.ids.singleOrNull()?.let(child::get)
				while (cur != null && below < 20 && cur in open) { below++; cur = child[cur] }
				rows += row(name, e.ids.map(::ItemId), cat, below)
			}
		}
		return listedOnly(rows, sort)
	}

	fun missing(onlyCategory: String?): List<Missing> = missingFrom(DConfig.data, children(), onlyCategory, TConfig.sort)

	fun sortLabel(sort: Sort) = if (sort == Sort.CHEAPEST) "Sort: Cheapest first" else "Sort: Most expensive first"
	fun nextLabel(sort: Sort) = if (sort == Sort.CHEAPEST) "Buy next cheapest" else "Buy next"

	fun buyCommand(row: Missing) = "ahs " + plain(row.buyName())

	internal var sendCommand: (String) -> Unit = { Client.runCommand(it) }

	internal fun buyAction(row: Missing): () -> Unit = {
		MuseumAhTarget.set(row.ids.map { it.skyblockId })
		sendCommand(buyCommand(row))
	}

	fun dispatchClick(targets: List<Pair<Rectangle, () -> Unit>>, inPanel: Boolean, mx: Double, my: Double, button: Int): Boolean {
		if (!inPanel) return false
		if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return true
		val hit = targets.lastOrNull { it.first.contains(mx, my) } ?: return true
		MenuPress.acted()
		hit.second()
		return true
	}

	private fun panelRows(screen: AbstractContainerScreen<*>): List<Missing>? {
		if (!TConfig.cheapestOverlay) return null
		val cat = category(screen)
		if (cat == null && !isOverview(screen)) return null
		return missing(if (TConfig.allCategories) null else cat)
	}

	private fun isMuseumScreen(screen: AbstractContainerScreen<*>) = category(screen) != null || isOverview(screen)

	private val panel = DonationPanel()
	private var panelScreen: AbstractContainerScreen<*>? = null

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
			if (screen !is AbstractContainerScreen<*> || !TConfig.cheapestOverlay || !isMuseumScreen(screen)) return@AfterInit
			panelScreen = screen
			HypixelStaticData.refreshLiveAuctionsNow()
			ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, click ->
				!MenuPress.press { panel.click(click.x(), click.y(), click.button()) }
			})
			ScreenMouseEvents.allowMouseScroll(screen).register(ScreenMouseEvents.AllowMouseScroll { _, mx, my, _, v -> !panel.scroll(mx, my, v) })
		})
	}

	@Subscribe
	fun onJeiAreas(event: HandledScreenPushREIEvent) {
		panel.rect?.let { event.block(it) }
	}

	private class DonationPanel {
		var rect: Rectangle? = null
		private var rows: List<Missing> = emptyList()
		private var offset = 0
		private var fits = 0
		private val targets = mutableListOf<Pair<Rectangle, () -> Unit>>()

		fun click(mx: Double, my: Double, button: Int): Boolean {
			val r = rect ?: return false
			return dispatchClick(targets.toList(), r.contains(mx, my), mx, my, button)
		}

		fun scroll(mx: Double, my: Double, v: Double): Boolean {
			val r = rect ?: return false
			if (!r.contains(mx, my)) return false
			offset = CheapestList.clamp(offset + (if (v > 0) -3 else 3), rows.size, fits)
			return true
		}

		fun draw(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, screen: AbstractContainerScreen<*>, rows: List<Missing>, mx: Int, my: Int) {
			this.rows = rows
			targets.clear()
			val font = Client.font
			val pal = MenuTheme.Palette.DEFAULT
			val anchor = screen.customGui?.getBounds()?.firstOrNull() ?: screen.getProperRectangle()
			val w = 190
			val x = if (TConfig.panelSide == Side.LEFT) anchor.x - 4 - w else anchor.x + anchor.width + 4
			if (x < 2 || x + w > screen.width - 2) { rect = null; return }
			val y = maxOf(2, anchor.y)
			val h = minOf(screen.height - 4 - y, maxOf(140, anchor.height))
			rect = Rectangle(x, y, w, h)
			MenuTheme.panel(ctx, x, y, w, h, pal)
			val cheapest = TConfig.sort == Sort.CHEAPEST
			val heading = (if (cheapest) "Cheapest to donate" else "Most expensive undonated") + " §7(${rows.size})"
			ctx.text(font, "§6§l" + MenuTheme.fit(heading, w - 12, font::width), x + 6, y + 6, MenuTheme.GOLD)
			val age = LiveAuctionPrices.current.ageLabel(System.currentTimeMillis())
			ctx.text(font, "§8" + MenuTheme.fit(age, w - 12, font::width), x + 6, y + 17, MenuTheme.DIM)

			fun button(bx: Int, by: Int, bw: Int, bh: Int, label: String, enabled: Boolean = true, action: () -> Unit) {
				val hover = enabled && MenuTheme.inside(mx, my, bx, by, bw, bh)
				MenuTheme.button(ctx, font, bx, by, bw, bh, MenuTheme.fit(label, bw - 6, font::width), MenuTheme.state(hover, false, enabled), palette = pal)
				if (enabled) targets += Rectangle(bx, by, bw, bh) to action
			}

			val controlsY = y + 28
			val half = (w - 15) / 2
			button(x + 6, controlsY, half, 16, if (cheapest) "Sort: Cheapest" else "Sort: Priciest") {
				TConfig.sort = if (TConfig.sort == Sort.CHEAPEST) Sort.MOST_EXPENSIVE else Sort.CHEAPEST
				TConfig.markDirty()
				offset = 0
			}
			button(x + 9 + half, controlsY, half, 16, nextLabel(TConfig.sort), rows.isNotEmpty()) { rows.firstOrNull()?.let { buyAction(it)() } }

			val listY = controlsY + 20
			val listH = y + h - 6 - listY
			val rowH = CheapestList.ROW_H
			ctx.fill(x + 6, listY, x + w - 6, listY + listH, MenuTheme.PANE)
			fits = CheapestList.visible(listH)
			offset = CheapestList.clamp(offset, rows.size, fits)
			if (rows.isEmpty()) ctx.text(font, "§7Open museum pages to record", x + 10, listY + 4, MenuTheme.GREY)
			ctx.enableScissor(x + 6, listY, x + w - 6, listY + listH)
			var hoverTip: Pair<List<String>, Pair<Int, Int>>? = null
			val fmt: (Double) -> String = { ApexFormatters.compactNumber(it) }
			for ((i, row) in rows.drop(offset).take(fits).withIndex()) {
				val ry = listY + 1 + i * rowH
				val hovered = MenuTheme.inside(mx, my, x + 7, ry, w - 22, rowH - 1)
				ctx.fill(x + 7, ry, x + w - 13, ry + rowH - 1, if (hovered) pal.borderHover else pal.border)
				ctx.fill(x + 8, ry + 1, x + w - 14, ry + rowH - 2, pal.fill)
				val price = priceText(row, fmt)
				val priceW = font.width(price)
				val buyW = 24
				val nameW = w - 22 - buyW - priceW - 10
				val shown = CheapestList.truncate(plain(row.name), nameW, font::width)
				val colour = if (row.ids.size > 1) "§d" else "§f"
				val extra = if (row.lowerTiers > 0) " §a+${row.lowerTiers}" else ""
				ctx.text(font, colour + shown + extra, x + 10, ry + 3, MenuTheme.WHITE)
				if (MenuTheme.inside(mx, my, x + 10, ry, minOf(nameW, font.width(shown)), rowH - 1))
					rowTooltip(row, shown, fmt, ::itemName).takeIf { it.isNotEmpty() }?.let { hoverTip = it to (mx to my) }
				ctx.text(font, price, x + w - 14 - buyW - 3 - priceW, ry + 3, MenuTheme.WHITE)
				button(x + w - 14 - buyW, ry + 1, buyW, rowH - 3, "Buy", action = buyAction(row))
			}
			ctx.disableScissor()
			hoverTip?.let { (lines, at) -> ctx.setTooltipForNextFrame(font, lines.map { Component.literal(it) }, java.util.Optional.empty(), at.first, at.second) }
			val maxOffset = CheapestList.maxScroll(rows.size, fits)
			if (maxOffset > 0) {
				val knobH = maxOf(8, listH * fits / rows.size)
				val knobY = listY + (listH - knobH) * offset / maxOffset
				ctx.fill(x + w - 11, listY, x + w - 8, listY + listH, 0xFF202024.toInt())
				ctx.fill(x + w - 11, knobY, x + w - 8, knobY + knobH, MenuTheme.SCROLL_KNOB)
			}
		}
	}

	@Subscribe
	fun onSlotRender(event: SlotRenderEvents.After) {
		if (!TConfig.highlightVacant) return
		val screen = Client.screen as? AbstractContainerScreen<*> ?: return
		val cat = category(screen) ?: return
		val slot = event.slot
		if (slot.container === Client.player?.inventory || !slot.item.`is`(Items.DYE.pick(DyeColor.LIME))) return
		event.context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8000FF00.toInt())
		if (TConfig.renderRealItem) realItem(slot.item, cat)?.let { event.context.item(it, slot.x, slot.y) }
	}

	@Subscribe
	fun onScreenRender(event: ScreenRenderPostEvent) {
		val screen = event.screen as? AbstractContainerScreen<*> ?: return
		val rows = panelRows(screen)
		if (rows == null || panelScreen !== screen) { panel.rect = null; return }
		panel.draw(event.drawContext, screen, rows, event.mouseX, event.mouseY)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.register(ManagedConfig.Category.MUSEUM, "museum", "Museum donation tracker") {
			onLiteral("resync") {
				onRun {
					DConfig.data.categories.clear()
					DConfig.markDirty()
					Client.showChat(tr("apex.museum.resync", "Museum data cleared. Open each museum page to record it again."))
				}
			}
		}
	}
}

object CheapestList {
	const val ROW_H = 14
	fun visible(listH: Int) = maxOf(1, (listH - 2) / ROW_H)
	fun maxScroll(total: Int, visible: Int) = maxOf(0, total - visible)
	fun clamp(scroll: Int, total: Int, visible: Int) = scroll.coerceIn(0, maxScroll(total, visible))

	fun truncate(name: String, width: Int, measure: (String) -> Int): String {
		if (measure(name) <= width) return name
		var keep = name.length
		while (keep > 0 && measure(name.substring(0, keep).trimEnd() + "…") > width) keep--
		return name.substring(0, keep).trimEnd() + "…"
	}

	fun tooltip(full: String, shown: String): String? = full.takeIf { shown != it }

	fun shown(scroll: Int, total: Int, visible: Int): IntRange {
		val from = clamp(scroll, total, visible)
		return from until minOf(total, from + visible)
	}
}
