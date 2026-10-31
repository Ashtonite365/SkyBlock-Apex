package dev.ashtonite365.apex.features.items.recipes

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.util.Faults
import dev.ashtonite365.apex.util.Client

class ViewerTab(
	val id: String,
	val label: Component,
	val order: Int,

	val open: (entryId: String?) -> Unit,
	val visible: () -> Boolean = { true },
)

interface ViewerHost {

	fun showsPanel(screen: Screen): Boolean

	fun panelRect(screen: Screen): PanelRect? = null

	fun hoveredItemId(screen: Screen): String? = null
}

data class PanelRect(val left: Int, val right: Int, val bottom: Int)

data class TabRect(val id: String, val x: Int, val y: Int, val w: Int, val h: Int) {
	fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
}

object ViewerTabs {

	const val TAB_H = 16

	const val GAP = 0

	const val PANEL_MARGIN = 6

	const val SEARCH_BAR_H = 24

	const val LABEL_PAD = 8

	private val tabs = LinkedHashMap<String, ViewerTab>()
	var host: ViewerHost? = null

	var skin: ((ctx: GuiGraphicsExtractor, box: TabRect, selected: Boolean, hover: Boolean, label: Component) -> Boolean)? = null
	var activeTab: String = "items"
		private set

	fun register(tab: ViewerTab) {
		tabs[tab.id] = tab
	}

	fun registered(): List<ViewerTab> = tabs.values.filter { it.visible() }.sortedBy { it.order }

	fun navigate(tabId: String, entryId: String? = null): Boolean {
		val tab = tabs[tabId] ?: return false
		activeTab = tabId
		Faults.attempt("Viewer tab ${tab.id} failed to open $entryId") { tab.open(entryId) }
		return true
	}

	fun resetForTest() { activeTab = "items"; tabs.clear() }

	fun layout(bottom: Int, panelLeft: Int, panelRight: Int, items: List<Pair<String, Int>>): List<TabRect> {
		val rows = mutableListOf<MutableList<Pair<String, Int>>>()
		var used = 0
		for (item in items) {
			val w = item.second
			if (rows.isEmpty() || (used > 0 && used + GAP + w > panelRight - panelLeft)) {
				rows.add(mutableListOf()); used = 0
			}
			used += (if (used > 0) GAP else 0) + w
			rows.last().add(item)
		}
		val out = mutableListOf<TabRect>()
		rows.forEachIndexed { rowIdx, row ->
			val y = bottom - (rows.size - rowIdx) * (TAB_H + GAP)
			var x = panelLeft
			for ((id, w) in row) {
				out.add(TabRect(id, x, y, w, TAB_H)); x += w + GAP
			}
		}
		return out
	}

	const val SEARCH_LIFT = 3

	data class JeiListConfig(
		val maxColumns: Int = 9,

		val alignment: String = "RIGHT",

		val background: Boolean = false,

		val centerSearch: Boolean = false,

		val maxRows: Int = 16,
	) {
		companion object {

			fun parse(ini: String): JeiListConfig {
				var section = ""
				val v = HashMap<String, String>()
				for (raw in ini.lineSequence()) {
					val l = raw.trim()
					if (l.startsWith("#") || l.isEmpty()) continue
					if (l.startsWith("[") && l.endsWith("]")) { section = l.substring(1, l.length - 1); continue }
					val i = l.indexOf('=')
					if (i > 0) v["$section.${l.substring(0, i).trim()}"] = l.substring(i + 1).trim()
				}
				val d = JeiListConfig()
				return JeiListConfig(
					v["ingredientList.maxColumns"]?.toIntOrNull()?.coerceIn(2, 100) ?: d.maxColumns,
					v["ingredientList.horizontalAlignment"]?.uppercase()?.takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: d.alignment,
					v["lists.drawBackground"]?.let { !it.equals("NONE", true) && !it.equals("false", true) } ?: d.background,
					v["search.centerSearch"]?.let { !it.equals("STANDARD", true) && !it.equals("false", true) } ?: d.centerSearch,
					v["ingredientList.maxRows"]?.toIntOrNull()?.coerceIn(1, 100) ?: d.maxRows,
				)
			}
		}
	}

	fun panelFromJeiLayout(guiRight: Int, guiBottom: Int, screenWidth: Int, screenHeight: Int, cfg: JeiListConfig = JeiListConfig()): PanelRect? {
		val border = 6; val search = 20; val inner = 2; val slot = 18
		val centered = cfg.centerSearch && guiBottom + search < screenHeight
		var ax = guiRight + border
		var aw = screenWidth - guiRight - 2 * border
		if (cfg.background) { ax += 7; aw -= 14 }
		val cols = minOf(aw / slot, cfg.maxColumns)
		if (cols < 2) return null
		val gridW = cols * slot
		val gx = ax + when (cfg.alignment) { "LEFT" -> 0; "CENTER" -> Math.round((aw - gridW) / 2f); else -> aw - gridW }
		val (left, width) = if (cfg.background) (gx - inner) to (gridW + 2 * inner) else gx to gridW

		val searchTop = screenHeight - border - search
		val bottom = (if (centered) screenHeight - border else searchTop) - SEARCH_LIFT
		return PanelRect(left, left + width, bottom)
	}

	fun panelFromGui(guiRight: Int, screenWidth: Int, screenHeight: Int): PanelRect {
		val left = guiRight + PANEL_MARGIN
		return PanelRect(left, (screenWidth - PANEL_MARGIN).coerceAtLeast(left + 40), screenHeight - SEARCH_BAR_H - SEARCH_LIFT)
	}

	fun estimatePanel(screen: Screen): PanelRect {
		val gr = (screen.width + 176) / 2
		return panelFromJeiLayout(gr, (screen.height + 166) / 2, screen.width, screen.height) ?: panelFromGui(gr, screen.width, screen.height)
	}

	fun currentRects(screen: Screen): List<TabRect> {
		val panel = host?.panelRect(screen) ?: estimatePanel(screen)
		val items = registered().map { it.id to Client.font.width(it.label) + LABEL_PAD * 2 }
		return layout(panel.bottom, panel.left, panel.right, items)
	}

	fun currentAreas(): List<TabRect> = Client.screen?.takeIf { shouldShow(it) }?.let { runCatching { currentRects(it) }.getOrNull() }.orEmpty()

	const val SELECTED_RISE = 2

	const val STRIP_MARGIN = 2

	fun stripExclusion(rects: List<TabRect>, panel: PanelRect): TabRect? {
		if (rects.isEmpty()) return null
		val left = minOf(panel.left, rects.minOf { it.x })
		val right = maxOf(panel.right, rects.maxOf { it.x + it.w })
		val top = rects.minOf { it.y } - SELECTED_RISE - STRIP_MARGIN
		val bottom = rects.maxOf { it.y + it.h }
		return TabRect("strip", left, top, right - left, bottom - top)
	}

	fun currentExclusion(): TabRect? = Client.screen?.takeIf { shouldShow(it) }?.let { s ->
		runCatching {
			val panel = host?.panelRect(s) ?: estimatePanel(s)
			val items = registered().map { it.id to Client.font.width(it.label) + LABEL_PAD * 2 }
			stripExclusion(layout(panel.bottom, panel.left, panel.right, items), panel)
		}.getOrNull()
	}

	private const val SLOT = 18
	private const val LIST_INSET = 6
	private const val NAVIGATION_H = 22

	fun rowsThatFit(stripTop: Int, cfg: JeiListConfig = JeiListConfig()): Int {
		val gridTop = LIST_INSET + NAVIGATION_H + (if (cfg.background) 7 else 0)
		return ((stripTop - 2 - gridTop) / SLOT).coerceAtLeast(0)
	}

	fun rowsLostToMaxRows(stripTop: Int, cfg: JeiListConfig): Int = (rowsThatFit(stripTop, cfg) - cfg.maxRows).coerceAtLeast(0)

	private fun shouldShow(screen: Screen) = registered().size > 1 && host?.showsPanel(screen) == true

	private var hooked = false

	fun hook() {
		if (hooked) return
		hooked = true
		ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, _, _ ->
			ScreenEvents.afterExtract(screen).register(ScreenEvents.AfterExtract { s, ctx, mx, my, _ ->
				if (shouldShow(s)) Faults.attempt("Viewer tabs render") { render(s, ctx, mx, my) }
			})

			ScreenKeyboardEvents.allowKeyPress(screen).register(ScreenKeyboardEvents.AllowKeyPress { s, key ->
				if (key.key() != InputConstants.KEY_H || !shouldShow(s) || tabs["howto"] == null) return@AllowKeyPress true

				if (dev.ashtonite365.apex.util.InventoryKeyClose.textInputFocused(s)) return@AllowKeyPress true
				val id = host?.hoveredItemId(s) ?: return@AllowKeyPress true
				navigate("howto", id)
				false
			})
			ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { s, click ->
				if (click.button() != InputConstants.MOUSE_BUTTON_LEFT || !shouldShow(s)) return@AllowMouseClick true
				val hit = currentRects(s).firstOrNull { it.contains(click.x(), click.y()) } ?: return@AllowMouseClick true

				dev.ashtonite365.apex.features.menus.MenuPress.press { dev.ashtonite365.apex.features.menus.MenuPress.acted(); navigate(hit.id, null) }
				false
			})
		})
	}

	const val TAB_FILL = 0xFF8B8B8B.toInt()
	const val TAB_FILL_HOVER = 0xFFA0A0A0.toInt()
	const val TAB_FILL_SELECTED = 0xFFC6C6C6.toInt()
	const val LIGHT = 0xFFFFFFFF.toInt()
	const val DARK = 0xFF555555.toInt()
	const val OUTLINE = 0xFF000000.toInt()

	fun drawnBox(r: TabRect, selected: Boolean): TabRect = if (selected) r.copy(y = r.y - 2, h = r.h + 2) else r

	private fun render(screen: Screen, ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		val rects = currentRects(screen)

		for (r in rects.sortedBy { it.id == activeTab }) {
			val selected = r.id == activeTab
			val hover = r.contains(mx.toDouble(), my.toDouble())
			val b = drawnBox(r, selected)
			val label0 = tabs[r.id]?.label
			if (label0 != null && skin?.invoke(ctx, b, selected, hover, label0) == true) continue
			ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, OUTLINE)
			ctx.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, if (selected) TAB_FILL_SELECTED else if (hover) TAB_FILL_HOVER else TAB_FILL)
			ctx.fill(b.x + 1, b.y + 1, b.x + b.w - 1, b.y + 2, LIGHT)
			ctx.fill(b.x + 1, b.y + 1, b.x + 2, b.y + b.h - 1, LIGHT)
			ctx.fill(b.x + 1, b.y + b.h - 2, b.x + b.w - 1, b.y + b.h - 1, DARK)
			ctx.fill(b.x + b.w - 2, b.y + 1, b.x + b.w - 1, b.y + b.h - 1, DARK)
			val label = tabs[r.id]?.label ?: continue
			ctx.centeredText(Client.font, label, b.x + b.w / 2, b.y + (b.h - 8) / 2, if (selected) 0xFF404040.toInt() else LIGHT)
		}
	}
}
