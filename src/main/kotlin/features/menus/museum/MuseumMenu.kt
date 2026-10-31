// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.museum

import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.features.museum.MuseumHelper
import dev.ashtonite365.apex.features.museum.MuseumInventoryAccess
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.bareText

@Config
object MuseumMenuSettings : MenuSettings("museum", panels = listOf("rings", "legend")) {
	val defaultSort by choice("default-sort") { MuseumSort.STATUS }

	val showCounts by toggle("show-counts") { true }
}

object MuseumMenu : CustomMenu {
	override val id = "museum"
	override val displayName = "Museum"
	override val title = MuseumParser.ANY_TITLE
	override val settings get() = MuseumMenuSettings

	override val defaultSize: Pair<Int, Int> get() = 380 to MuseumInventoryAccess.menuHeight(250, MuseumInventoryAccess.enabled)

	private const val BARE_TITLE_WINDOW_MS = 60_000L

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		MenuRegistry.register(this)
	}

	override fun create(screen: AbstractContainerScreen<*>): MenuView? {
		val t = MenuText.clean(screen.title.bareText)
		val bare = !t.contains("museum", ignoreCase = true)
		return if (bare && System.currentTimeMillis() - MuseumMemory.seenAtMs > BARE_TITLE_WINDOW_MS) null else MuseumView(screen)
	}
}

class MuseumView(screen: AbstractContainerScreen<*>) : ApexViewBase<MuseumModel>(screen) {
	private val c get() = MuseumMenuSettings.customisation
	private var sort = MuseumMenuSettings.defaultSort
	private var filter = MuseumFilter.ALL

	private val gridScroll = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey("filter=$filter") }
	private var hoverSlot: Int? = null

	override fun palette() = c.palette
	override fun parse(title: String, slots: List<SlotText>) = MuseumParser.parse(title, slots)?.also {
		slots.firstOrNull { MilestonesParser.ENTRY_NAME.matches(it.cleanName) }?.let { MilestoneCache.recordLore(it.cleanLore) }
		val now = System.currentTimeMillis()
		MuseumMemory.remember(it, now)
		MuseumAppraisalCache.remember(MuseumAppraisal.fromSlots(slots))
		if (MuseumMenuSettings.showCounts) MuseumHelper.MuseumCounts.recordModel(it, now)
		if (!it.isOverview) MuseumMemory.seenAtMs = now
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: MuseumModel, mx: Int, my: Int) {
		val pad = 6
		hoverSlot = null
		val vw = font.width("Vanilla") + 10

		val (search, _) = MuseumSearchIcon.split(MuseumTabs.of(if (m.isOverview) m.categories else MuseumMemory.categories).more)
		val hdr = MuseumSearchIcon.header(x + w - vw - 4, y)
		search?.let { sc ->
			val r = hdr.icon
			button(ctx, mx, my, r.x, r.y, r.w, r.h, "🔍") { _ -> openCategory(m, sc) }
			if (MenuTheme.inside(mx, my, r.x, r.y, r.w, r.h))
				ctx.setComponentTooltipForNextFrame(font, listOf(net.minecraft.network.chat.Component.literal(MuseumSearchIcon.TOOLTIP)), mx, my)
		}
		val backX = if (search != null) hdr.backX else x + w - vw - 52
		m.backSlot?.let { s -> button(ctx, mx, my, backX, y + 3, 44, 14, "← Back") { clickSlot(s) } }
		appraisalBox(ctx, mx, my, x + w - vw - 4, vw)

		val titleW = MuseumSearchIcon.titleMaxW(x, pad, x + w - vw - 4, y, search != null, m.backSlot != null, x + w - vw - 52)
		ctx.text(font, MenuTheme.fit("§6§lMuseum" + (m.category?.let { " §7› §f$it" } ?: ""), titleW, font::width), x + pad, y + 7, MenuTheme.WHITE)

		var tabY = y + MuseumAppraisalBox.CONTENT_Y
		m.stats?.let { st -> tabY += statsStrip(ctx, st, mx, my, x + pad, tabY, w - 2 * pad) + 4 }

		val tabs = MuseumTabs.of(if (m.isOverview) m.categories else MuseumMemory.categories).let { t -> t.copy(more = MuseumSearchIcon.split(t.more).second) }
		val active = m.category
		if (active != null && !pageInit) { tabs.pageOf(active)?.let { carouselPage = it }; pageInit = true }
		val shown = tabs.carousel(carouselPage)
		if (tabs.main.isNotEmpty()) {
			val aw = 14
			if (tabs.pages > 1) {
				button(ctx, mx, my, x + pad, tabY, aw, 16, "‹") { _ -> carouselPage = Math.floorMod(carouselPage - 1, tabs.pages) }
				button(ctx, mx, my, x + w - pad - aw, tabY, aw, 16, "›") { _ -> carouselPage = Math.floorMod(carouselPage + 1, tabs.pages) }
			}
			val inner = if (tabs.pages > 1) w - 2 * pad - 2 * (aw + 3) else w - 2 * pad
			val ix = if (tabs.pages > 1) x + pad + aw + 3 else x + pad
			val tw = (inner - (MuseumTabs.PER_PAGE - 1) * 3) / MuseumTabs.PER_PAGE

			val labels = shown.map { tabLabel(m, it) }
			val sc = MuseumBottomRow.scaleToFit(labels, labels.indices.map { MuseumBottomRow.Rect(0, 0, tw, 16) }, font::width)
			shown.forEachIndexed { i, cat -> tab(ctx, m, cat, mx, my, ix + i * (tw + 3), tabY, tw, labels[i], sc) }
		}
		val bodyY = tabY + 22

		val inv = MuseumInventoryAccess.shows(MuseumInventoryAccess.enabled, screen.menu.slots.size)
		contentBottom = MuseumInventoryAccess.contentBottom(y, h, 6, inv)
		bodyBottom = contentBottom - MuseumBottomRow.layout(x + 6, contentBottom, w - 12, bottomLabels(tabs)).height - (if (tabs.more.isNotEmpty()) 4 else 0)
		if (m.isOverview) overview(ctx, m, shown, mx, my, bodyY) else category(ctx, m, mx, my, bodyY)
		bottomRow(ctx, m, tabs, mx, my)

		if (inv && removeGuard.prompt() == null) MuseumInventoryAccess.guard("museum panel '${screen.title.string}'") {
			val (ix, iy) = MuseumInventoryAccess.panelOrigin(x, y, w, h, 6)
			inventoryPanel(ctx, mx, my, ix, iy)
		}
		confirmOverlay(ctx, mx, my)
		hoverSlot?.let { s -> stacks[s]?.let { st -> ctx.setComponentTooltipForNextFrame(font, Screen.getTooltipFromItem(Client.game, st), mx, my) } }
	}

	private fun appraisalBox(ctx: GuiGraphicsExtractor, mx: Int, my: Int, vx: Int, vw: Int) {
		val d = MuseumAppraisalCache.current()
		val label = MuseumAppraisal.label(d)
		val r = MuseumAppraisalBox.rect(vx, y, vw, font.width(label))
		ctx.fill(r.x, r.y, r.x + r.w, r.y + r.h, 0x80000000.toInt())
		ctx.text(font, if (d?.value != null) "§6$label" else "§8$label", r.x + 3, r.y + 2, MenuTheme.WHITE)
		if (MenuTheme.inside(mx, my, r.x, r.y, r.w, r.h)) MuseumAppraisal.tooltip(d).takeIf { it.isNotEmpty() }?.let { t ->
			ctx.setComponentTooltipForNextFrame(font, t.map { net.minecraft.network.chat.Component.literal(it) }, mx, my)
		}
	}

	private val removeGuard = MuseumRemoveGuard()
	private var carouselPage = 0
	private var pageInit = false
	private var bodyBottom = 0

	private var contentBottom = 0

	private fun isActiveTab(m: MuseumModel, cat: MuseumCategory) = m.category?.let { MuseumParser.sameCategory(it, cat.name) } == true
	private fun tabLabel(m: MuseumModel, cat: MuseumCategory) = cat.name

	private fun tab(ctx: GuiGraphicsExtractor, m: MuseumModel, cat: MuseumCategory, mx: Int, my: Int, tx: Int, ty: Int, tw: Int, label: String, sc: Float) {
		val active = isActiveTab(m, cat)
		val enabled = m.isOverview || active || m.backSlot != null
		button(ctx, mx, my, tx, ty, tw, 16, "", active = active, enabled = enabled) { _ -> openCategory(m, cat) }
		val lw = font.width(label) * sc
		ctx.pose().pushMatrix(); ctx.pose().translate(tx + (tw - lw) / 2f, ty + (16 - 8 * sc) / 2f); ctx.pose().scale(sc, sc)
		ctx.text(font, label, 0, 0, if (enabled) MenuTheme.WHITE else MenuTheme.GREY); ctx.pose().popMatrix()
	}

	private fun bottomLabels(tabs: MuseumTabs): List<String> {
		val pr = MilestoneCache.data()?.progress
		val extra = listOfNotNull(pr?.milestone?.let { "§7Milestone §a$it§7/§f${pr.milestones ?: MilestonesParser.TOTAL}" },
			pr?.nextPct?.let { "§7next §e$it%" }).joinToString(" §8· ")
		return tabs.more.map { if (extra.isNotEmpty() && MilestonesParser.ENTRY_NAME.matches(it.name)) "${it.name} §8· $extra" else it.name }
	}

	private fun bottomRow(ctx: GuiGraphicsExtractor, m: MuseumModel, tabs: MuseumTabs, mx: Int, my: Int) {
		if (tabs.more.isEmpty()) return
		val labels = bottomLabels(tabs)
		val rects = MuseumBottomRow.layout(x + 6, contentBottom, w - 12, labels).rects
		val sc = MuseumBottomRow.scaleToFit(labels, rects, font::width)
		tabs.more.forEachIndexed { i, cat ->
			val r = rects[i]
			val active = m.category?.let { MuseumParser.sameCategory(it, cat.name) } == true
			val hover = MenuTheme.inside(mx, my, r.x, r.y, r.w, r.h)
			MenuTheme.button(ctx, font, r.x, r.y, r.w, r.h, "", MenuTheme.state(hover, active, true), palette = palette())
			val tw = font.width(labels[i]) * sc
			ctx.pose().pushMatrix(); ctx.pose().translate(r.x + (r.w - tw) / 2f, r.y + (r.h - 8 * sc) / 2f); ctx.pose().scale(sc, sc)
			ctx.text(font, labels[i], 0, 0, MenuTheme.WHITE); ctx.pose().popMatrix()
			area(r.x, r.y, r.w, r.h) { _ -> openCategory(m, cat) }
		}
	}

	private fun statsStrip(ctx: GuiGraphicsExtractor, st: MuseumStats, mx: Int, my: Int, sx: Int, sy: Int, sw: Int): Int {
		val rows = MuseumParser.packStatsLines(st.lines, sw - 10) { font.width(it) }.take(2)
		val sh = 6 + rows.size.coerceAtLeast(1) * 10
		val hover = MenuTheme.inside(mx, my, sx, sy, sw, sh)
		val p = palette()
		MenuTheme.panel(ctx, sx, sy, sw, sh, if (hover) p.copy(border = p.borderHover) else p)
		rows.forEachIndexed { i, r -> ctx.text(font, "§7$r", sx + 5, sy + 4 + i * 10, MenuTheme.GREY) }
		area(sx, sy, sw, sh) { b -> if (b == 0) clickSlot(st.slot) }
		if (hover) hoverSlot = st.slot
		return sh
	}

	private fun openCategory(m: MuseumModel, cat: MuseumCategory) {

		val slot = MuseumTabStep.decide(m.isOverview, cat.slot, m.backSlot, active = !m.isOverview && isActiveTab(m, cat)) ?: return
		clickSlot(slot)
		if (!m.isOverview) dev.ashtonite365.apex.util.Client.showChat(net.minecraft.network.chat.Component.literal("§6[Apex] §7Back to the Museum: now click §e${cat.name}§7 again."))
	}

	private fun overview(ctx: GuiGraphicsExtractor, m: MuseumModel, shown: List<MuseumCategory>, mx: Int, my: Int, top: Int) {
		val p = palette()
		val cols = 3
		val gap = 6
		val cw = (w - 12 - (cols - 1) * gap) / cols
		val ch = 58
		shown.forEachIndexed { i, cat ->
			val cx = x + 6 + (i % cols) * (cw + gap); val cy = top + (i / cols) * (ch + gap)
			if (cy + ch > bodyBottom) return@forEachIndexed
			val hover = MenuTheme.inside(mx, my, cx, cy, cw, ch)
			MenuTheme.panel(ctx, cx, cy, cw, ch, if (hover) p.copy(border = p.borderHover) else p)
			area(cx, cy, cw, ch) { clickSlot(cat.slot) }
			stacks[cat.slot]?.let { ctx.item(it, cx + 6, cy + 6) }
			ctx.text(font, MenuTheme.fit(cat.name, cw - 30, font::width), cx + 26, cy + 10, MenuTheme.WHITE)
			val frac = cat.fraction ?: MuseumHelper.MuseumCounts.shown(cat.name, cat.donated, cat.total)?.fraction
			val shownCount = MuseumHelper.MuseumCounts.shown(cat.name, cat.donated, cat.total)
			val count = MuseumHelper.MuseumCounts.label(shownCount)
			ctx.text(font, count, cx + 8, cy + 30, MenuTheme.WHITE)
			if (shownCount?.cached == true) ctx.text(font, "§8cached", cx + 8 + font.width(count) + 4, cy + 30, MenuTheme.DIM)
			frac?.let { ctx.text(font, "§7" + pct(it), cx + 8, cy + 42, MenuTheme.GREY) }
			if (c.shows("rings")) ring(ctx, cx + cw - 22, cy + ch - 20, 13, frac ?: 0.0, p.accent)
			if (hover) hoverSlot = cat.slot
		}
	}

	private fun category(ctx: GuiGraphicsExtractor, m: MuseumModel, mx: Int, my: Int, top: Int) {
		if (m.entries.any { it.removable } || m.countOnly != null) { specialList(ctx, m, mx, my, top); return }
		val p = palette()

		val donated = m.donatedShown; val total = m.totalShown
		val frac = if (total > 0) donated.toDouble() / total else 0.0
		var sx = x + 6
		if (c.shows("rings")) { ring(ctx, sx + 10, top + 9, 9, frac, p.accent); sx += 26 }
		ctx.text(font, "§a$donated§7/§f$total §8(${pct(frac)})", sx, top + 5, MenuTheme.WHITE)
		var fx = x + w - 6
		val sortLabel = "Sort: " + sort.name.lowercase().replaceFirstChar { it.uppercase() }
		val sw = font.width(sortLabel) + 10; fx -= sw
		button(ctx, mx, my, fx, top, sw, 16, sortLabel) { _ -> sort = MuseumSort.entries[(sort.ordinal + 1) % MuseumSort.entries.size] }
		for (f in MuseumFilter.entries.reversed()) {
			val l = f.name.lowercase().replaceFirstChar { it.uppercase() }
			val fw = font.width(l) + 10; fx -= fw + 3
			button(ctx, mx, my, fx, top, fw, 16, l, active = filter == f) { _ -> filter = f }
		}

		val gridTop = top + 22
		val legendH = if (c.shows("legend")) 12 else 0
		val pagerH = if (m.prevSlot != null || m.nextSlot != null) 20 else 0
		val gridBottom = bodyBottom - legendH - pagerH
		val cell = 20
		val cols = maxOf(1, (w - 12) / cell)
		val list = MuseumParser.sort(MuseumParser.filter(m.entries, filter), sort)

		val view = dev.ashtonite365.apex.features.menus.MenuBounds(x + 1, gridTop, w - 2, maxOf(0, gridBottom - gridTop))
		val totalRows = (list.size + cols - 1) / cols
		gridScroll.layout(view, if (totalRows == 0) 0 else totalRows * cell - 2)
		val first = gridScroll.offset / cell
		val ox = x + 6 + ((w - 12) - cols * cell) / 2
		beginClip(ctx, view)
		list.drop(first * cols).take(cols * (view.h / cell + 2)).forEachIndexed { j, e ->
			val i = first * cols + j
			val cx = ox + (i % cols) * cell; val cy = gridTop + (i / cols) * cell - gridScroll.offset
			val tint = when (e.status) {
				MuseumStatus.DONATED -> 0xFF1E4A28.toInt(); MuseumStatus.MISSING -> 0xFF5A1E1E.toInt()
				MuseumStatus.LOCKED -> 0xFF2A2A30.toInt(); MuseumStatus.UNKNOWN -> p.fill
			}
			val hover = hovered(mx, my, cx, cy, cell - 2, cell - 2)
			ctx.fill(cx, cy, cx + cell - 2, cy + cell - 2, if (hover) p.borderHover else p.border)
			ctx.fill(cx + 1, cy + 1, cx + cell - 3, cy + cell - 3, tint)
			stacks[e.slot]?.let { ctx.item(it, cx + 1, cy + 1) }
			if (e.status == MuseumStatus.LOCKED) {

				ctx.fill(cx + 1, cy + 1, cx + cell - 3, cy + cell - 3, 0x90101014.toInt())
				ctx.text(font, "§8🔒", cx + cell - 10, cy + cell - 11, MenuTheme.DIM)
			} else area(cx, cy, cell - 2, cell - 2) { b -> if (b == 0) removeGuard.click(e)?.let { clickSlot(it) } else if (!e.removable) clickSlot(e.slot, b) }
			if (hover) hoverSlot = e.slot
		}
		endClip(ctx)
		if (list.isEmpty()) ctx.text(font, "§8Nothing here with this filter", ox, gridTop + 4, MenuTheme.DIM)
		if (gridScroll.max > 0) MenuTheme.scrollbar(ctx, x + w - 4, view.y, view.h, view.h, gridScroll.contentHeight, gridScroll.offset)
		if (legendH > 0) {
			val ly = gridBottom + 2
			var lx = x + 8
			for ((col, l) in listOf(0xFF3C9A50.toInt() to "Donated", 0xFFB03A3A.toInt() to "Missing", 0xFF60606A.toInt() to "Locked")) {
				ctx.fill(lx, ly + 1, lx + 7, ly + 8, col); lx += 10
				ctx.text(font, "§7$l", lx, ly, MenuTheme.GREY); lx += font.width(l) + 10
			}
		}
		if (pagerH > 0) {
			val py = bodyBottom - 16
			m.prevSlot?.let { s -> button(ctx, mx, my, x + 6, py, 60, 16, "‹ Prev") { clickSlot(s) } }
			val pl = if (m.page != null && m.pages != null) "Page ${m.page}/${m.pages}" else ""
			ctx.text(font, "§7$pl", x + (w - font.width(pl)) / 2, py + 4, MenuTheme.GREY)
			m.nextSlot?.let { s -> button(ctx, mx, my, x + w - 66, py, 60, 16, "Next ›") { clickSlot(s) } }
		}
	}

	private fun specialList(ctx: GuiGraphicsExtractor, m: MuseumModel, mx: Int, my: Int, top: Int) {
		val p = palette()
		val countText = m.countOnly?.let { "§a$it §7donated" } ?: "§a${m.donatedShown}§7/§f${m.totalShown}"
		ctx.text(font, countText, x + 6, top + 5, MenuTheme.WHITE)
		var fx = x + w - 6
		m.sortControl?.let { sc ->
			val l = "Sort: " + (sc.selected ?: "?")
			val bw = font.width(l) + 10; fx -= bw

			button(ctx, mx, my, fx, top, bw, 16, l) { b -> clickSlot(sc.slot, if (b == 1) 1 else 0) }
			fx -= 3
		}
		m.extraDetailsSlot?.let { s ->
			val l = "Extra Details: " + when (m.extraDetailsOn) { true -> "ON"; false -> "OFF"; null -> "?" }
			val bw = font.width(l) + 10; fx -= bw
			button(ctx, mx, my, fx, top, bw, 16, l, active = m.extraDetailsOn == true) { _ -> clickSlot(s) }
		}
		val rowH = 22
		var ry = top + 22
		if (m.extraDetailsOn == false) { ctx.text(font, "§8Turn on Extra Details to see values", x + 6, ry, MenuTheme.DIM); ry += 12 }
		for (e in m.entries.sortedBy { it.slot }) {
			if (ry + rowH > bodyBottom) break
			val hover = MenuTheme.inside(mx, my, x + 6, ry, w - 12, rowH - 2)
			MenuTheme.panel(ctx, x + 6, ry, w - 12, rowH - 2, if (hover) p.copy(border = p.borderHover) else p)
			stacks[e.slot]?.let { ctx.item(it, x + 8, ry + 1) }
			val d = e.details
			ctx.text(font, MenuTheme.fit("§f${e.name}" + (d?.rarity?.let { " §d$it" } ?: "") + (d?.atriumSlot?.let { " §8Atrium #$it" } ?: ""), w - 40, font::width), x + 28, ry + 2, MenuTheme.WHITE)
			val info = if (d == null || (d.value == null && d.cleanValue == null)) (if (m.extraDetailsOn == false) "§8values hidden (Extra Details off)" else "§8no details")
				else listOfNotNull(d.value?.let { "§6$it" }, d.cleanValue?.let { "§7clean §6$it" }, d.donated?.let { "§7donated $it" }, d.created?.let { "§7created $it" }).joinToString(" §8· ")
			ctx.text(font, MenuTheme.fit(info, w - 40, font::width), x + 28, ry + 11, MenuTheme.GREY)
			area(x + 6, ry, w - 12, rowH - 2) { b -> if (b == 0) removeGuard.click(e)?.let { clickSlot(it) } }
			if (hover) hoverSlot = e.slot
			ry += rowH
		}
	}

	private fun confirmOverlay(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		val q = removeGuard.prompt() ?: return
		val bw = maxOf(160, font.width(q) + 20); val bh = 46
		val bx = x + (w - bw) / 2; val by = y + (h - bh) / 2
		areas.clear()
		ctx.fill(x, y, x + w, y + h, 0xA0000000.toInt())
		MenuTheme.panel(ctx, bx, by, bw, bh, palette())
		ctx.text(font, "§c$q", bx + 10, by + 8, MenuTheme.WHITE)
		button(ctx, mx, my, bx + 10, by + 24, 60, 16, "§cRemove") { _ -> removeGuard.confirm()?.let { clickSlot(it) } }
		button(ctx, mx, my, bx + bw - 70, by + 24, 60, 16, "Cancel") { _ -> removeGuard.cancel() }
	}

	private fun pct(f: Double) = "%.0f%%".format(java.util.Locale.ROOT, f * 100)

	private fun ring(ctx: GuiGraphicsExtractor, cx: Int, cy: Int, r: Int, frac: Double, colour: Int) {
		val steps = 72
		for (i in 0 until steps) {
			val a = Math.PI * 2 * i / steps - Math.PI / 2
			val px = cx + (cos(a) * r).toInt(); val py = cy + (sin(a) * r).toInt()
			ctx.fill(px - 1, py - 1, px + 1, py + 1, if (i < frac * steps) colour else MenuTheme.DIM)
		}
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		gridScroll.scroll(amount)
		return true
	}
}

object MuseumTabStep {
	fun decide(isOverview: Boolean, categorySlot: Int, backSlot: Int?, active: Boolean): Int? = when {
		isOverview -> categorySlot
		active -> null
		else -> backSlot
	}
}
