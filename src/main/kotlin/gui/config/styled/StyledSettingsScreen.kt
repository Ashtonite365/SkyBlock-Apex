// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.gui.config.styled

import com.google.auto.service.AutoService
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.style.GuiCanvas
import dev.ashtonite365.apex.features.menus.style.MenuAnim
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.ModSettingsPages
import dev.ashtonite365.apex.features.menus.style.Shapes
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.style.StyleAnim
import dev.ashtonite365.apex.features.menus.style.StyleBar
import dev.ashtonite365.apex.features.menus.style.StyleWidgets
import dev.ashtonite365.apex.features.menus.style.StyleState
import dev.ashtonite365.apex.gui.config.ActionCodec
import dev.ashtonite365.apex.gui.config.AllConfigsGui
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.ApexConfigScreenProvider
import dev.ashtonite365.apex.gui.config.BooleanHandler
import dev.ashtonite365.apex.gui.config.ChoiceHandler
import dev.ashtonite365.apex.gui.config.IntegerHandler
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.SettingsEditorKind
import dev.ashtonite365.apex.gui.config.SettingsMenuPlan
import dev.ashtonite365.apex.gui.config.SettingsTabs
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.SectionPage
import dev.ashtonite365.apex.util.ScreenUtil.setScreenLater
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

@AutoService(ApexConfigScreenProvider::class)
class StyledSettingsProvider : ApexConfigScreenProvider {
	override val key: String = "styled"
	override fun open(search: String?, parent: Screen?): Screen = StyledSettingsScreen(search, parent)
}

class StyledSettingsScreen(private val search: String?, private val parent: Screen?, private val focus: dev.ashtonite365.apex.util.data.ManagedConfig? = null) : Screen(Component.literal("SkyBlock Apex")) {
	private sealed interface Line {
		data class Card(val title: String) : Line

		data class Module(val title: String, val key: String) : Line
		data class Opt(val option: ManagedOption<*>, val kind: SettingsEditorKind?) : Line
		data class Jump(val label: String, val page: SectionPage<ManagedOption<*>>) : Line
		data class Own(val label: String, val desc: String, val value: () -> String, val toggle: Boolean, val on: () -> Boolean, val click: (Int) -> Unit) : Line
	}

	private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val onClick: (Int) -> Unit) {
		fun has(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
	}

	private val pages by lazy { runCatching { SettingsTabs.pages() }.getOrDefault(emptyList()) }
	private val sections by lazy { MainSection.entries.filter { m -> pages.any { it.main == m } } }

	private var section: MainSection? = null

	private var extra: ModPage? = null

	private fun tabs(): List<Any?> = listOf<Any?>(null) + sections + ModSettingsPages.all
	private fun tabLabel(t: Any?) = when (t) { null -> "Look & Feel"; is ModPage -> t.title; else -> SettingsTabs.label(t as MainSection).string }
	private val currentTab: Any? get() = extra ?: section
	private var page: SectionPage<ManagedOption<*>>? = null
	private var scroll = 0
	private lateinit var box: EditBox
	private var query = search ?: ""
	private val bar = StyleBar()
	private val hits = mutableListOf<Hit>()
	private var pressed: Hit? = null

	private val openedAt by lazy { dev.ashtonite365.apex.features.menus.style.ScreenTransitions.openStartNs() }
	private var dockHoverSince = 0L
	private var dockHovering = false
	private var dockScroll = 0
	private var lastDockSel: Int? = -1
	private var dockScrollRect: IntArray? = null
	private var dockCount = 0
	private var tip: List<Component>? = null

	private var tabScroll = 0
	private var chipScroll = 0
	private var tabStripRect: IntArray? = null
	private var chipStripRect: IntArray? = null
	private var tabTotal = 0; private var chipTotal = 0
	private var lastTabSel: Any? = Unit; private var lastChipSel: Any? = Unit

	private var dockRect: IntArray? = null
	private val dockHits = mutableListOf<Hit>()

	private val st get() = MenuStyle.state
	private val sp get() = MenuStyle.palette
	private val frame get() = StyledSettingsLayout.frame(width, height)

	private var focused = false

	companion object {

		fun <T> moduleRuns(items: List<T>, moduleOf: (T) -> String): List<Pair<String, List<T>>> =
			items.groupBy(moduleOf).map { it.key to it.value }

		fun <T> searchGroups(pages: List<SectionPage<T>>, query: String, header: (dev.ashtonite365.apex.settings.Subsection) -> String,
			label: (T) -> String, desc: (T) -> String): List<Pair<String, List<T>>> {
			val out = LinkedHashMap<String, MutableList<T>>()
			for (p in pages) {
				var head: String? = null; var moduleHit = false
				for (r in SettingsMenuPlan.rows(p)) when (r) {
					is SettingsMenuPlan.Row.Header -> { head = header(r.subsection); moduleHit = StyledSettingsLayout.matches(query, head.substringAfter(" › "), "") }
					is SettingsMenuPlan.Row.Entry -> if (head != null && (moduleHit || StyledSettingsLayout.matches(query, label(r.item), desc(r.item))))
						out.getOrPut(head) { mutableListOf() }.let { if (r.item !in it) it += r.item }
					else -> {}
				}
			}
			return out.map { it.key to it.value.toList() }
		}

		fun pageFor(pages: List<SectionPage<ManagedOption<*>>>, config: dev.ashtonite365.apex.util.data.ManagedConfig): SectionPage<ManagedOption<*>>? =
			pages.firstOrNull { p -> SettingsMenuPlan.rows(p).any { (it as? SettingsMenuPlan.Row.Entry)?.item?.element === config } }
	}

	override fun init() {
		super.init()
		if (!focused) { focused = true; focus?.let { pageFor(pages, it) }?.let { section = it.main; page = it } }
		val f = frame
		box = EditBox(font, f.x + 110, f.y + 5, minOf(150, f.w - 300).coerceAtLeast(60), 14, Component.literal("Search"))
		box.value = query
		box.setBordered(false)
		addRenderableWidget(box)
	}

	private fun pagesOf(m: MainSection) = pages.filter { it.main == m }

	private fun lookLines(): List<Line> {
		val s = st
		return listOf(
			Line.Card("Menu style"),
			Line.Own("Style", "One style for this screen, the profile viewer and every SkyBlock chest menu.", { dev.ashtonite365.apex.features.menus.style.StyleChoice.of(s).label }, false, { false }) { b ->

				val all = dev.ashtonite365.apex.features.menus.style.StyleChoice.entries
				MenuStyle.choose(all[(all.indexOf(dev.ashtonite365.apex.features.menus.style.StyleChoice.of(MenuStyle.state)) + (if (b == 1) -1 else 1) + all.size) % all.size]) },
			Line.Own("Forge: dark mode", "Forge has a light and a dark look (the same switch is in every menu's top bar).", { "" }, true, { s.forgeMode == dev.ashtonite365.apex.features.menus.style.ForgeMode.DARK }) { MenuStyle.toggleForgeMode() },
			Line.Own("Style every chest menu", "Menus without their own Apex layout get a styled grid (off: Hypixel's chest).", { "" }, true, { s.wrapAll && s.customMenus }) { MenuStyle.setWrapAll(!(MenuStyle.state.wrapAll && MenuStyle.state.customMenus)) },
			Line.Own("Apex button in the inventory", "A logo button next to the recipe book that opens this menu (client-side only).", { "" }, true, { s.inventoryButton }) { MenuStyle.update { it.copy(inventoryButton = !it.inventoryButton) } },
			Line.Card("Animations"),
			Line.Own("Open / close animations", "Animate menus as they open and close.", { "" }, true, { s.animate }) { MenuStyle.update { it.copy(animate = !it.animate) } },
			Line.Own("Animation", "Fade, a circle that opens outward and closes in, or glitch.", { s.anim.label }, false, { false }) { b ->
				MenuStyle.update { it.copy(anim = MenuAnim.entries[(it.anim.ordinal + (if (b == 1) -1 else 1) + MenuAnim.entries.size) % MenuAnim.entries.size]) } },
			Line.Own("Reduce motion", "A short fade in and an instant close, whatever the animation.", { "" }, true, { s.reduceMotion }) { MenuStyle.update { it.copy(reduceMotion = !it.reduceMotion) } },
		)
	}

	private fun customMenusLines(): List<Line> {
		val out = mutableListOf<Line>(Line.Card("Custom Menus"))
		var key = ""
		for (r in dev.ashtonite365.apex.features.menus.style.CustomMenusPage.rows) when (r) {
			is dev.ashtonite365.apex.features.menus.style.CustomMenusPage.Row.Module -> { key = "custom-menus|${r.title}"; out += Line.Module(r.title, key) }
			is dev.ashtonite365.apex.features.menus.style.CustomMenusPage.Row.Toggle -> if (key !in folded)
				out += Line.Own(r.label, r.desc, { "" }, true, { r.on(MenuStyle.state) }) { r.flip() }
			is dev.ashtonite365.apex.features.menus.style.CustomMenusPage.Row.Cycle -> if (key !in folded)
				out += Line.Own(r.label, r.desc, { r.value(MenuStyle.state) }, false, { false }) { b -> r.step(if (b == 1) -1 else 1) }
		}
		return out
	}

	private fun lines(): List<Line> {
		if (query.isNotBlank()) return searchGroups(pages, query, { SettingsTabs.label(it.main).string + " › " + SettingsTabs.label(it).string },
			{ it.labelText.string }, { it.labelDescription.string }).flatMap { (head, opts) ->
			listOf(Line.Card(head)) + withModules(head, opts.filter(::shown)) }.let { ls ->
			ls.filterIndexed { i, l -> l !is Line.Card || ls.getOrNull(i + 1).let { it is Line.Opt || it is Line.Module } } } + modSearchLines(query)
		extra?.let { return modPageLines(it) }
		val m = section ?: return lookLines()
		val pg = page ?: pagesOf(m).firstOrNull() ?: return if (m == MainSection.CUSTOM_MENUS) customMenusLines() else emptyList()
		val out = mutableListOf<Line>()

		if (m == MainSection.CUSTOM_MENUS && pg == pagesOf(m).firstOrNull()) out += customMenusLines()
		var card = ""; val pending = mutableListOf<ManagedOption<*>>()
		fun flush() { out += withModules(card, pending); pending.clear() }
		for (r in SettingsMenuPlan.rows(pg)) when (r) {
			is SettingsMenuPlan.Row.Shortcut -> out += Line.Jump(SettingsTabs.label(r.target).string, pages.firstOrNull { p -> p.subsections.any { it.subsection == r.target } } ?: pg)
			is SettingsMenuPlan.Row.Header -> { flush(); card = SettingsTabs.label(r.subsection).string; out += Line.Card(card) }
			is SettingsMenuPlan.Row.Entry -> if (shown(r.item)) pending += r.item
		}
		flush()
		return out
	}

	private val folded = mutableSetOf<String>()

	private fun withModules(card: String, opts: List<ManagedOption<*>>): List<Line> =
		moduleRuns(opts) { it.element.name }.let { runs ->
			if (runs.size < 2) opts.map { Line.Opt(it, SettingsMenuPlan.moulConfigKind(it.handler)) }
			else runs.flatMap { (name, items) ->
				val key = "$card|$name"
				listOf(Line.Module(items.first().element.labelText.string, key)) +
					(if (key in folded) emptyList() else items.map { Line.Opt(it, SettingsMenuPlan.moulConfigKind(it.handler)) })
			}
		}

	private fun shown(o: ManagedOption<*>) = SettingsTabs.optionKey(o).let { it !in AllConfigsGui.RETIRED_KEYS && it !in ModSettingsPages.movedKeys() }

	private fun optionByKey(key: String): ManagedOption<*>? = pages.asSequence().flatMap { SettingsMenuPlan.rows(it).asSequence() }
		.mapNotNull { (it as? SettingsMenuPlan.Row.Entry)?.item }.firstOrNull { SettingsTabs.optionKey(it) == key }

	private fun modLine(r: ModSettingsPages.Row, key: String): Line? = when (r) {
		is ModSettingsPages.Row.Module -> Line.Module(r.label, key)
		is ModSettingsPages.Row.Toggle -> Line.Own(r.label, r.desc, { "" }, true, r.on) { r.flip() }
		is ModSettingsPages.Row.Cycle -> Line.Own(r.label, r.desc, r.value, false, { false }) { b -> r.step(if (b == 1) -1 else 1) }
		is ModSettingsPages.Row.Option -> optionByKey(r.key)?.let { Line.Opt(it, SettingsMenuPlan.moulConfigKind(it.handler)) }
	}

	private fun modPageLines(p: ModPage): List<Line> {
		val out = mutableListOf<Line>(Line.Card(p.title))
		var key = ""
		for (r in p.rows()) {
			if (r is ModSettingsPages.Row.Module) { key = "mod-${p.id}|${r.label}"; out += Line.Module(r.label, key); continue }
			if (key !in folded) modLine(r, key)?.let { out += it }
		}
		return out
	}

	private fun modSearchLines(q: String): List<Line> = ModSettingsPages.all.flatMap { p ->
		val hits = ModSettingsPages.search(p, q) { k -> optionByKey(k)?.let { it.labelText.string to it.labelDescription.string } }.mapNotNull { modLine(it, "") }
		if (hits.isEmpty()) emptyList() else listOf<Line>(Line.Card(p.title)) + hits
	}

	private fun openEditor(o: ManagedOption<*>) = setScreenLater(o.element.getOptionEditor(o, this))

	override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		if (!dev.ashtonite365.apex.features.menus.style.MenuStyle.clearOutsideNow()) super.extractBackground(context, mouseX, mouseY, delta)
	}

	override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		super.extractRenderState(g, mouseX, mouseY, delta)
		if (box.value != query) { query = box.value; scroll = 0 }
		hits.clear(); dockHits.clear(); tip = null; dockRect = null; dockScrollRect = null
		val c = GuiCanvas(g, font); val p = sp; val s = st; val f = frame
		if (!p.clearOutside) g.fill(0, 0, width, height, p.backdrop)
		val el0 = (System.nanoTime() - openedAt) / 1_000_000
		dev.ashtonite365.apex.features.menus.style.Frost.fade = dev.ashtonite365.apex.features.menus.MenuChrome.frostFade(StyleAnim.kind(s), StyleAnim.settingsOpenReveal(s, el0))

		Shapes.round(c, f.x, f.y + 3, f.w, f.h, p.cardRadius, p.shadow)
		if (p.blurWorld) c.frost(f.x, f.y, f.w, f.h, p.cardRadius)
		Shapes.roundFrame(c, f.x, f.y, f.w, f.h, p.cardRadius, p.window, p.border, p.style == Style.SKYGLASS)
		g.fill(f.x + p.cardRadius, f.y, f.x + f.w - p.cardRadius, f.y + 1, p.rimHighlight)
		dev.ashtonite365.apex.features.menus.style.ForgeMetal.plate(c, p, f.x, f.y, f.w, f.h)

		g.text(font, "SkyBlock Apex", f.x + 10, f.y + 8, p.accent, false)
		Shapes.roundFrame(c, box.x - 4, box.y - 2, box.width + 8, 16, p.controlRadius, p.well, p.border)
		if (box.value.isEmpty() && !box.isFocused) g.text(font, "Search…", box.x, box.y + 1, p.textDim, false)
		box.extractRenderState(g, mouseX, mouseY, delta)
		val geo = StyleBar.geometry(f.x + f.w - 8, f.y + 5, s, height)

		val chips = section?.let { pagesOf(it) }?.takeIf { it.size > 1 && query.isBlank() }
		val content = StyledSettingsLayout.content(f, s.style, chips != null)
		tabStripRect = null; chipStripRect = null
		body(g, c, content, mouseX, mouseY)
		if (s.style == Style.FORGE) forgeTabs(g, c, f, mouseX, mouseY) else Unit
		chips?.let { pageChips(g, c, it, content.x, content.y - StyledSettingsLayout.TAB_H - 2, content.w, mouseX, mouseY) }
		if (s.style == Style.SKYGLASS) dock(g, c, f, mouseX, mouseY)
		g.nextStratum()
		bar.drawButton(c, p, s, geo, mouseX, mouseY, false)
		tip?.let { g.setComponentTooltipForNextFrame(font, it, mouseX, mouseY) }

		val el = (System.nanoTime() - openedAt) / 1_000_000
		StyleWidgets.cover(c, StyleAnim.kind(s), p.window, f.x, f.y, f.w, f.h, StyleAnim.settingsOpenReveal(s, el), openedAt, el)

		bar.drawList(c, p, s, geo, mouseX, mouseY)
	}

	private fun press(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) =
		StyleWidgets.press(MenuTheme.inside(mx, my, x, y, w, h), pressed?.let { it.x == x && it.y == y } == true, false)

	private fun btn(g: GuiGraphicsExtractor, c: GuiCanvas, x: Int, y: Int, w: Int, h: Int, label: String, mx: Int, my: Int, selected: Boolean = false, onClick: (Int) -> Unit) {
		StyleWidgets.button(c, sp, x, y, w, h, label, if (selected) dev.ashtonite365.apex.features.menus.style.Press.ACTIVE else press(x, y, w, h, mx, my))
		hits += Hit(x, y, w, h, onClick)
	}

	private fun forgeTabs(g: GuiGraphicsExtractor, c: GuiCanvas, f: dev.ashtonite365.apex.features.menus.MenuBounds, mx: Int, my: Int) {
		val y = f.y + StyledSettingsLayout.HEADER
		val all = tabs()
		val labels = all.map { tabLabel(it) }
		val sel = all.indexOf(currentTab).takeIf { query.isBlank() } ?: -1
		strip(g, c, f.x + 8, y, f.w - 16, StyledSettingsLayout.TAB_H, labels, labels.map { font.width(it) + 12 }, sel, true, mx, my) { i -> select(all[i]) }
		g.fill(f.x + 8, y + StyledSettingsLayout.TAB_H + 1, f.x + f.w - 8, y + StyledSettingsLayout.TAB_H + 2, sp.glow)
	}

	private fun strip(g: GuiGraphicsExtractor, c: GuiCanvas, vx: Int, y: Int, view: Int, h: Int, labels: List<String>, widths: List<Int>,
			sel: Int, tabs: Boolean, mx: Int, my: Int, onClick: (Int) -> Unit) {
		val S = StyledSettingsLayout.Strip
		val total = S.total(widths); val lefts = S.lefts(widths)
		var scroll = if (tabs) tabScroll else chipScroll
		val selKey: Any? = if (sel >= 0) labels[sel] else null
		if (selKey != (if (tabs) lastTabSel else lastChipSel) && sel >= 0) scroll = S.ensureVisible(scroll, lefts[sel], widths[sel], total, view)
		scroll = S.clamp(scroll, total, view)
		if (tabs) { tabScroll = scroll; tabTotal = total; tabStripRect = intArrayOf(vx, y, view, h); lastTabSel = selKey }
		else { chipScroll = scroll; chipTotal = total; chipStripRect = intArrayOf(vx, y, view, h); lastChipSel = selKey }

		if (sp.style == Style.SKYGLASS) StyleWidgets.overlaySurface(c, sp, vx - 2, y - 1, view + 4, h + 2, sp.controlRadius)
		g.enableScissor(vx, y - 1, vx + view, y + h + 2)
		for ((i, label) in labels.withIndex()) {
			val x = vx + lefts[i] - scroll; val w = widths[i]
			if (x + w <= vx || x >= vx + view) continue
			if (tabs) StyleWidgets.button(c, sp, x, y, w, h, label, if (i == sel) dev.ashtonite365.apex.features.menus.style.Press.ACTIVE else press(x, y, w, h, mx, my))
			else {
				val hov = MenuTheme.inside(mx, my, x, y, w, h)
				Shapes.pill(c, x, y, w, h, if (i == sel) sp.accent else if (hov) sp.buttonHover else sp.buttonFill, sp.border)
				g.text(font, label, x + 7, y + 5, if (i == sel) sp.textOnAccent else sp.text, sp.textShadow && i != sel)
			}
			S.visible(x, w, vx, view)?.let { (hx, hw) -> hits += Hit(hx, y, hw, h) { onClick(i) } }
		}
		g.disableScissor()
		if (S.moreLeft(scroll)) { g.fill(vx, y, vx + 10, y + h, sp.overlay); g.text(font, "‹", vx + 2, y + (h - 8) / 2, sp.text, false) }
		if (S.moreRight(scroll, total, view)) { g.fill(vx + view - 10, y, vx + view, y + h, sp.overlay); g.text(font, "›", vx + view - 7, y + (h - 8) / 2, sp.text, false) }
	}

	private fun dock(g: GuiGraphicsExtractor, c: GuiCanvas, f: dev.ashtonite365.apex.features.menus.MenuBounds, mx: Int, my: Int) {
		val now = System.nanoTime() / 1_000_000
		val L = StyledSettingsLayout
		val box = L.dockBox(f); val x = box[0]; val y = box[1]; val h = box[2]
		val narrowHover = MenuTheme.inside(mx, my, x, y, StyledSettingsLayout.DOCK, h)
		val wideHover = dockHovering && MenuTheme.inside(mx, my, x, y, StyledSettingsLayout.DOCK_WIDE, h)
		val hov = narrowHover || wideHover
		if (hov != dockHovering) { dockHovering = hov; dockHoverSince = now }
		val w = StyledSettingsLayout.dockWidth(hov, now - dockHoverSince).let { if (!hov && now - dockHoverSince >= StyledSettingsLayout.DOCK_MS) StyledSettingsLayout.DOCK else it }
		if (w > StyledSettingsLayout.DOCK) {

			StyleWidgets.overlaySurface(c, sp, x, y, w, h, sp.dockRadius)
			dockRect = intArrayOf(x, y, w, h)
		} else {
			Shapes.round(c, x, y + 2, w, h, sp.dockRadius, sp.shadow)
			Shapes.roundFrame(c, x, y, w, h, sp.dockRadius, sp.card, sp.border, glass = true)
		}
		val all = tabs()

		val selIdx = if (query.isBlank()) all.indexOf(currentTab).takeIf { it >= 0 } else null
		if (selIdx != lastDockSel) { dockScroll = L.dockKeepVisible(dockScroll, selIdx, all.size, h); lastDockSel = selIdx }
		dockScroll = dockScroll.coerceIn(0, L.dockMaxScroll(all.size, h))
		dockScrollRect = intArrayOf(x, y, w, h); dockCount = all.size
		val clipTop = y + L.DOCK_PAD; val clipBottom = y + h - L.DOCK_PAD
		g.enableScissor(x, clipTop, x + w, clipBottom)
		var iy = clipTop - dockScroll
		for (m in all) {
			if (iy + 20 <= clipTop || iy >= clipBottom) { iy += L.DOCK_ITEM; continue }
			val label = tabLabel(m)
			val sel = m == currentTab && query.isBlank()
			val ih = MenuTheme.inside(mx, my, x + 2, iy, w - 4, 20)
			if (sel || ih) Shapes.round(c, x + 3, iy, w - 6, 20, sp.controlRadius, if (sel) sp.accent else sp.buttonHover)
			val ic = if (m == null) "✦" else StyledSettingsLayout.icon(label)
			g.text(font, ic, x + (StyledSettingsLayout.DOCK - font.width(ic)) / 2, iy + 6, if (sel) sp.textOnAccent else sp.text, sp.textShadow && !sel)
			if (w > 60) g.text(font, font.plainSubstrByWidth(label, w - StyledSettingsLayout.DOCK - 6), x + StyledSettingsLayout.DOCK, iy + 6, if (sel) sp.textOnAccent else sp.text, sp.textShadow && !sel)

			val hy0 = maxOf(iy, clipTop); val hy1 = minOf(iy + 20, clipBottom)
			Hit(x + 2, hy0, w - 4, hy1 - hy0) { select(m) }.let { hits += it; dockHits += it }
			iy += L.DOCK_ITEM
		}
		g.disableScissor()
	}

	private fun pageChips(g: GuiGraphicsExtractor, c: GuiCanvas, list: List<SectionPage<ManagedOption<*>>>, x0: Int, y: Int, view: Int, mx: Int, my: Int) {
		val cur = page ?: list.first()
		val labels = list.map { SettingsTabs.label(it).string }
		strip(g, c, x0, y, view, StyledSettingsLayout.TAB_H, labels, labels.map { font.width(it) + 14 }, list.indexOf(cur), false, mx, my) { i -> page = list[i]; scroll = 0 }
	}

	private fun select(m: Any?) { extra = m as? ModPage; section = m as? MainSection; page = null; scroll = 0; box.value = ""; query = "" }

	private fun body(g: GuiGraphicsExtractor, c: GuiCanvas, a: dev.ashtonite365.apex.features.menus.MenuBounds, mx: Int, my: Int) {
		val ls = lines()
		g.enableScissor(a.x, a.y, a.x + a.w, a.y + a.h)
		var y = a.y + 4 - scroll
		var cardTop = -1

		fun closeCard(bottom: Int) { if (cardTop >= 0) Shapes.ring(c, a.x, cardTop, a.w, bottom - cardTop, sp.cardRadius, sp.border) }
		val bodyHits = mutableListOf<Hit>()
		val savedHits = hits.toList(); hits.clear()
		for (l in ls) {
			when (l) {
				is Line.Card -> {
					if (cardTop >= 0) { closeCard(y); y += 6 }
					cardTop = y
					Shapes.round(c, a.x, y, a.w, StyledSettingsLayout.CARD_HEAD, sp.cardRadius, sp.raised)
					g.text(font, l.title, a.x + 8, y + 4, sp.accent, false)
					y += StyledSettingsLayout.CARD_HEAD + 2
				}
				is Line.Module -> {
					if (y + StyledSettingsLayout.MODULE_H > a.y && y < a.y + a.h) moduleHeader(g, l, a, y, mx, my)
					y += StyledSettingsLayout.MODULE_H
				}
				else -> {
					if (y + StyledSettingsLayout.ROW > a.y && y < a.y + a.h) row(g, c, l, a, y, mx, my)
					y += StyledSettingsLayout.ROW
				}
			}
		}
		closeCard(y + 2)
		g.disableScissor()
		val total = y + scroll - a.y
		maxScroll = maxOf(0, total - a.h + 8)

		bodyHits += hits.filter { it.y + it.h > a.y && it.y < a.y + a.h }
		hits.clear(); hits += savedHits; hits += bodyHits
		if (ls.isEmpty()) g.text(font, "Nothing here.", a.x + 8, a.y + 10, sp.textDim, false)
	}
	private var maxScroll = 0

	private fun moduleHeader(g: GuiGraphicsExtractor, l: Line.Module, a: dev.ashtonite365.apex.features.menus.MenuBounds, y: Int, mx: Int, my: Int) {
		val lx = a.x + 10; val right = a.x + a.w - 8
		val ty = y + StyledSettingsLayout.MODULE_GAP
		g.fill(lx, ty, right, ty + 1, sp.border)
		val hover = MenuTheme.inside(mx, my, lx, ty, right - lx, StyledSettingsLayout.MODULE_H - StyledSettingsLayout.MODULE_GAP)
		val arrow = if (l.key in folded) "▸ " else "▾ "
		g.text(font, font.plainSubstrByWidth(arrow + l.title, right - lx), lx, ty + 4, if (hover) sp.accent else sp.textDim, false)
		hits += Hit(lx, ty, right - lx, StyledSettingsLayout.MODULE_H - StyledSettingsLayout.MODULE_GAP) { if (!folded.remove(l.key)) folded += l.key }
	}

	@Suppress("UNCHECKED_CAST")
	private fun row(g: GuiGraphicsExtractor, c: GuiCanvas, l: Line, a: dev.ashtonite365.apex.features.menus.MenuBounds, y: Int, mx: Int, my: Int) {
		val lx = a.x + 10; val right = a.x + a.w - 8; val cy = y + 3
		val ctlW = 74
		fun label(text: String, desc: String) {
			g.text(font, font.plainSubstrByWidth(text, right - ctlW - lx - 8), lx, y + 7, sp.text, false)
			if (MenuTheme.inside(mx, my, lx, y, right - ctlW - lx, StyledSettingsLayout.ROW) && desc.isNotBlank())
				tip = listOf(Component.literal(text)) + font.split(Component.literal(desc), 220).map { seq ->
					val sb = StringBuilder(); seq.accept { _, _, cp -> sb.appendCodePoint(cp); true }; Component.literal("§7$sb") }
		}
		when (l) {
			is Line.Own -> {
				label(l.label, l.desc)
				if (l.toggle) { StyleWidgets.toggle(c, sp, right - 22, cy + 4, if (l.on()) 1.0 else 0.0); hits += Hit(right - 26, y, 28, StyledSettingsLayout.ROW, l.click) }
				else btn(g, c, right - ctlW, cy, ctlW, 16, l.value(), mx, my, onClick = l.click)
			}
			is Line.Jump -> btn(g, c, lx, cy, minOf(a.w - 20, font.width(l.label) + 30), 16, "→ ${l.label}", mx, my) { extra = null; section = l.page.main; page = l.page; scroll = 0 }
			is Line.Opt -> {
				val o = l.option
				if (FeatureRisk.purpleBoxed(o)) {
					g.fill(lx - 4, y, right + 4, y + StyledSettingsLayout.ROW, PURPLE_FILL)
					g.fill(lx - 4, y, right + 4, y + 1, PURPLE); g.fill(lx - 4, y + StyledSettingsLayout.ROW - 1, right + 4, y + StyledSettingsLayout.ROW, PURPLE)
					g.fill(lx - 4, y, lx - 3, y + StyledSettingsLayout.ROW, PURPLE); g.fill(right + 3, y, right + 4, y + StyledSettingsLayout.ROW, PURPLE)
				}
				label(o.labelText.string, if (FeatureRisk.purpleBoxed(o)) "⚠ " + FeatureRisk.HARD_HEADER + " " + o.labelDescription.string else o.labelDescription.string)
				when (val h = o.handler) {
					is BooleanHandler -> { val on = (o as ManagedOption<Boolean>).get()
						StyleWidgets.toggle(c, sp, right - 22, cy + 4, if (on) 1.0 else 0.0)
						hits += Hit(right - 26, y, 28, StyledSettingsLayout.ROW) { o.set(!o.get()) } }
					is ChoiceHandler<*> -> {
						val opt = o as ManagedOption<Any>
						val u = h.universe as List<Any>
						val name = runCatching { (h.renderer as dev.ashtonite365.apex.gui.config.EnumRenderer<Any>).getName(opt, opt.get()).string }.getOrDefault(opt.get().toString())
						btn(g, c, right - ctlW - 30, cy, ctlW + 30, 16, font.plainSubstrByWidth(name, ctlW + 22), mx, my) { b ->
							if (u.isNotEmpty()) { val i = u.indexOf(opt.get()); opt.set(u[((if (i < 0) 0 else i) + (if (b == 1) -1 else 1) + u.size) % u.size]) } }
					}
					is IntegerHandler -> {
						val opt = o as ManagedOption<Int>
						btn(g, c, right - ctlW, cy, 16, 16, "−", mx, my) { opt.set(dev.ashtonite365.apex.gui.config.IntegerStep.next(opt.get(), -1, h.min, h.max, dev.ashtonite365.apex.keybindings.SavedKeyBinding.isShiftDown())) }
						val v = opt.get().toString()
						g.text(font, v, right - ctlW / 2 - font.width(v) / 2, cy + 4, sp.text, false)
						btn(g, c, right - 16, cy, 16, 16, "+", mx, my) { opt.set(dev.ashtonite365.apex.gui.config.IntegerStep.next(opt.get(), 1, h.min, h.max, dev.ashtonite365.apex.keybindings.SavedKeyBinding.isShiftDown())) }
					}
					is ActionCodec -> btn(g, c, right - ctlW, cy, ctlW, 16, "Run", mx, my) { h.runAction() }
					else -> btn(g, c, right - ctlW, cy, ctlW, 16, "Edit…", mx, my) { openEditor(o) }
				}
			}
			is Line.Card, is Line.Module -> {}
		}
	}

	override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
		val mx = event.x(); val my = event.y()
		val f = frame
		if (bar.click(StyleBar.geometry(f.x + f.w - 8, f.y + 5, st, height), mx, my)) return true
		if (super.mouseClicked(event, doubled)) return true
		val h = (if (dockRect?.let { StyledSettingsLayout.inRect(it, mx, my) } == true) pickDock(mx, my) else hits.lastOrNull { it.has(mx, my) }) ?: return dockRect?.let { StyledSettingsLayout.inRect(it, mx, my) } == true
		pressed = h
		h.onClick(event.button())
		return true
	}

	override fun mouseReleased(event: MouseButtonEvent): Boolean { pressed = null; return super.mouseReleased(event) }

	private fun pickDock(mx: Double, my: Double) = dockHits.lastOrNull { it.has(mx, my) }

	override fun mouseScrolled(x: Double, y: Double, h: Double, v: Double): Boolean {
		val S = StyledSettingsLayout.Strip
		dockScrollRect?.takeIf { StyledSettingsLayout.inRect(it, x, y) }?.let {
			dockScroll = (dockScroll - (v * StyledSettingsLayout.DOCK_ITEM).toInt()).coerceIn(0, StyledSettingsLayout.dockMaxScroll(dockCount, it[3])); return true }
		tabStripRect?.takeIf { StyledSettingsLayout.inRect(it, x, y) }?.let { tabScroll = S.wheel(tabScroll, h, v, tabTotal, it[2]); return true }
		chipStripRect?.takeIf { StyledSettingsLayout.inRect(it, x, y) }?.let { chipScroll = S.wheel(chipScroll, h, v, chipTotal, it[2]); return true }
		scroll = (scroll - (v * 20).toInt()).coerceIn(0, maxScroll)
		return true
	}

	override fun charTyped(event: CharacterEvent): Boolean = super.charTyped(event)
	override fun keyPressed(event: KeyEvent): Boolean = super.keyPressed(event)

	override fun removed() {
		super.removed()
		val f = frame
		if (StyleAnim.closeMs(st) > 0) dev.ashtonite365.apex.features.menus.Ghosts.start(
			dev.ashtonite365.apex.features.menus.Ghosts.Ghost(f.x, f.y, f.w, f.h, System.nanoTime(), openedAt))
	}

	override fun onClose() { minecraft?.gui?.setScreen(parent) }
	override fun isPauseScreen() = false
}

private typealias ModPage = ModSettingsPages.Page

private const val PURPLE = 0xFFA040E0.toInt()
private const val PURPLE_FILL = 0x30A040E0
