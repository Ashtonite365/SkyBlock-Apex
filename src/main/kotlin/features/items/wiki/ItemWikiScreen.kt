// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.items.wiki

import dev.ashtonite365.apex.features.menus.MenuPress
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URLEncoder
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.util.Client

class ItemWikiScreen(
	private val parent: Screen?, private val start: WikiTarget?, private val itemName: String, private val stack: ItemStack? = null,
) : Screen(Component.literal("Item Wiki")) {

	private sealed interface View {
		data class Loading(val title: String) : View
		data class Message(val text: String, val hint: Boolean) : View
		data class Ready(val page: WikiDoc.Page, val stale: Boolean) : View
	}

	private var view: View = View.Loading("Loading…")
	private val history = WikiHistory()
	private var loadGen = 0
	private var started = false

	private var pendingAnchor: String? = null
	private var pendingScroll: Int? = null

	private var layoutState = WikiLayout.State()
	private var laid: WikiLayout.Laid? = null
	private var laidKey: LayoutKey? = null
	private var wantedKey: LayoutKey? = null
	private val scroll = WikiScroll()

	private var panel = PanelRect(0, 0, 0, 0)
	private val headerH = 16
	private val footerH = 11
	private val bodyX get() = panel.x + 6
	private val bodyY get() = panel.y + headerH + 4
	private val bodyW get() = panel.w - 12 - BAR_W - 2
	private val bodyH get() = panel.h - headerH - footerH - 6
	private val trackX get() = panel.x + panel.w - 4 - BAR_W
	private var buttons: List<Pair<String, Btn>> = emptyList()

	private var sectionsOpen = false
	private var sectionsTop = 0
	private var draggingThumb = false
	private var thumbGrab = 0

	private var keyArmed = false
	private val openedAt = System.currentTimeMillis()

	private data class Btn(val x: Int, val y: Int, val w: Int, val h: Int, val label: String) {
		fun hit(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
	}

	override fun init() {
		parent?.let { p -> if (p.width != width || p.height != height) runCatching { p.init(width, height) } }
		panel = PanelRect.centred(width, height, w = (width * 0.8).toInt().coerceIn(PanelRect.MIN_W, 560), h = (height * 0.88).toInt().coerceIn(PanelRect.MIN_H, 420))
		layoutButtons()
		if (!started) {
			started = true
			if (start == null) view = View.Message("No wiki page could be found for \"$itemName\".", hint = true)
			else {
				history.open(start.title, start.anchor)
				open(start)
			}
		}
		scroll.resize(laid?.height ?: 0, bodyH)
	}

	private fun layoutButtons() {
		val y = panel.y + 2
		val h = headerH - 4
		val out = ArrayList<Pair<String, Btn>>()
		var right = panel.x + panel.w - 3
		fun r(id: String, label: String) { val w = font.width(label) + 8; right -= w; out += id to Btn(right, y, w, h, label); right -= 3 }
		r("close", "x")
		r("browser", "Open in browser")
		r("sections", "Sections ▾")
		out += "back" to Btn(panel.x + 3, y, font.width("< Back") + 8, h, "< Back")
		buttons = out
	}

	private fun button(id: String): Btn? = buttons.firstOrNull { it.first == id }?.second
	private fun visible(id: String): Boolean = when (id) {
		"back" -> history.canGoBack
		"sections" -> (view as? View.Ready)?.page?.sections?.isNotEmpty() == true
		else -> true
	}

	private fun open(target: WikiTarget) {
		val gen = ++loadGen
		view = View.Loading(target.title)
		sectionsOpen = false
		pendingAnchor = target.anchor
		ItemWiki.load(target) { r ->
			if (gen != loadGen) return@load
			when (r) {
				is WikiLoaderCore.Result.Fail -> view = View.Message(r.message, hint = true)
				is WikiLoaderCore.Result.Ok -> {
					val loaded = r.loaded
					if (history.current?.title != loaded.page.title) history.retitle(loaded.page.title)
					pendingAnchor = loaded.anchor ?: pendingAnchor
					Apex.coroutineScope.launch(Dispatchers.Default) {
						val page = runCatching { WikiDoc.parse(loaded.page.title, loaded.page.html) }.getOrNull()
						Client.onClientThread {
							if (gen != loadGen) return@onClientThread
							view = when {
								page == null || page.blocks.isEmpty() -> View.Message("The page \"${loaded.page.title}\" has no readable text.", hint = false)
								else -> View.Ready(page, loaded.stale)
							}
							layoutState = WikiLayout.State()
							laid = null; laidKey = null; wantedKey = null
						}
					}
				}
			}
		}
	}

	private fun navigate(title: String, anchor: String?) {
		history.current?.scroll = scroll.offset
		history.open(title, anchor)
		pendingScroll = null
		open(WikiTarget(title, anchor, rule = "link"))
	}

	private fun goBack() {
		history.current?.scroll = scroll.offset
		val e = history.goBack() ?: return
		val target = WikiTarget(e.title, null, rule = "back")
		open(target)
		pendingAnchor = null
		pendingScroll = e.scroll
	}

	private fun currentPage(): WikiDoc.Page? = (view as? View.Ready)?.page

	private fun requestLayout() {
		val p = currentPage() ?: return
		val smooth = ItemWiki.TConfig.smoothFont
		val dark = ItemWiki.TConfig.darkPage
		val key = LayoutKey(p, bodyW, smooth, dark, layoutState)
		if (key == laidKey || key == wantedKey) return
		wantedKey = key
		WikiFonts.prime(p.title + p.chars() + EXTRA_CHARS)
		val m = WikiFonts.measure()
		val theme = if (dark) WikiLayout.Theme.DARK else WikiLayout.Theme.LIGHT
		val gen = loadGen
		Apex.coroutineScope.launch(Dispatchers.Default) {
			val l = runCatching { WikiLayout.layout(p, key.w, m, theme, WikiPrices.Live, key.state, smooth) }.getOrNull()
			Client.onClientThread {
				if (gen != loadGen || wantedKey != key || l == null) return@onClientThread
				val samePage = laidKey?.page === p
				laid = l; laidKey = key; wantedKey = null
				scroll.resize(l.height, bodyH)
				if (!samePage) {
					scroll.home()
					pendingScroll?.let { scroll.scrollTo(it) }
					pendingAnchor?.let { a -> p.indexOfAnchor(a)?.let { scroll.scrollTo(l.yOfBlock(it)) } }
					pendingScroll = null; pendingAnchor = null
				}
			}
		}
	}

	private fun theme() = if (ItemWiki.TConfig.darkPage) WikiLayout.Theme.DARK else WikiLayout.Theme.LIGHT

	private fun frameOf(img: WikiDoc.Img): WikiDoc.Img =
		if (img.frames.isEmpty()) img else img.frames[(((System.currentTimeMillis() - openedAt) / FRAME_MS) % img.frames.size).toInt()]

	override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		val p = parent
		if (p != null) runCatching {
			p.extractBackground(ctx, -1, -1, partialTick)
			ctx.nextStratum()
			p.extractRenderState(ctx, -1, -1, partialTick)
			ctx.nextStratum()
		}
		ctx.fill(0, 0, width, height, DIM)
	}

	override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
		if (!keyArmed && !runCatching { ItemWiki.TConfig.key.isPressed(atLeast = true) }.getOrDefault(false)) keyArmed = true
		requestLayout()
		val theme = theme()
		val p = panel

		ctx.fill(p.x - 1, p.y - 1, p.x + p.w + 1, p.y + p.h + 1, theme.rule)
		ctx.fill(p.x, p.y, p.x + p.w, p.y + p.h, theme.page)
		ctx.fill(p.x, p.y, p.x + p.w, p.y + headerH, HEADER_BG)
		drawHeader(ctx, mouseX, mouseY)
		drawFooter(ctx, mouseX, mouseY, theme)

		when (val v = view) {
			is View.Loading -> centred(ctx, "Loading ${v.title}…", theme.muted)
			is View.Message -> {
				val lines = font.split(Component.literal(v.text), bodyW - 10)
				var y = bodyY + bodyH / 2 - (lines.size * 10) / 2 - (if (v.hint) 6 else 0)
				for (l in lines) { ctx.centeredText(font, l, bodyX + bodyW / 2, y, theme.text); y += 10 }
				if (v.hint) ctx.centeredText(font, "Open in browser to search the wiki, or Esc to close.", bodyX + bodyW / 2, y + 4, theme.muted)
			}
			is View.Ready -> {
				val l = laid
				if (l == null) centred(ctx, "Loading ${v.page.title}…", theme.muted)
				else drawPage(ctx, l, theme, mouseX, mouseY)
			}
		}
		drawScrollbar(ctx, theme)
		if (sectionsOpen) drawSections(ctx, mouseX, mouseY)
	}

	private fun centred(ctx: GuiGraphicsExtractor, text: String, colour: Int) =
		ctx.centeredText(font, text, bodyX + bodyW / 2, bodyY + bodyH / 2 - 4, colour)

	private fun drawHeader(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		var left = panel.x + 4
		for ((id, b) in buttons) {
			if (!visible(id)) continue
			val hover = b.hit(mx.toDouble(), my.toDouble()) && !sectionsOpen || (id == "sections" && sectionsOpen)
			val bg = if (hover) BTN_HOVER else BTN_BG
			ctx.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg)
			ctx.centeredText(font, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, 0xFFFFFFFF.toInt())
			if (hover) ctx.requestCursor(CursorTypes.POINTING_HAND)
			if (id == "back") left = b.x + b.w + 5
		}
		val stale = (view as? View.Ready)?.stale == true
		val title = "§fHypixel SkyBlock Wiki" + (if (stale) " §e⚠ offline copy" else "")
		val right = buttons.filter { visible(it.first) && it.first != "back" }.minOfOrNull { it.second.x } ?: (panel.x + panel.w)
		if (left + font.width(title) < right - 4) ctx.text(font, title, left, panel.y + (headerH - 8) / 2, 0xFFFFFFFF.toInt(), true)
	}

	private fun attributionLine(): String = WikiAttribution.line(currentPage()?.title ?: history.current?.title)

	private fun footerBox(): Btn {
		val text = attributionLine()
		val w = minOf(font.width(text), panel.w - 12)
		return Btn(panel.x + 6, panel.y + panel.h - footerH + 1, w, 9, text)
	}

	private fun drawFooter(ctx: GuiGraphicsExtractor, mx: Int, my: Int, theme: WikiLayout.Theme) {
		val b = footerBox()
		ctx.fill(panel.x, b.y - 2, panel.x + panel.w, b.y - 1, theme.rule)
		val hover = b.hit(mx.toDouble(), my.toDouble())
		val text = font.plainSubstrByWidth(b.label, b.w)
		val comp = Component.literal(text).withStyle { it.withUnderlined(hover) }
		ctx.text(font, comp, b.x, b.y, theme.muted, false)
		if (hover) ctx.requestCursor(CursorTypes.POINTING_HAND)
	}

	private fun drawPage(ctx: GuiGraphicsExtractor, l: WikiLayout.Laid, theme: WikiLayout.Theme, mouseX: Int, mouseY: Int) {
		val top = scroll.offset
		val inBody = !sectionsOpen && mouseX >= bodyX && mouseX < bodyX + bodyW && mouseY >= bodyY && mouseY < bodyY + bodyH
		val lx = mouseX - bodyX
		val ly = mouseY - bodyY + top
		val hover = if (inBody) l.opAt(lx, ly, HOVERABLE) else null
		val hoverHref = if (inBody) l.hrefAt(lx, ly, ::frameOf) else null
		ctx.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH)
		ctx.pose().pushMatrix()
		ctx.pose().translate(bodyX.toFloat(), (bodyY - top).toFloat())
		val bottom = top + bodyH
		for (o in l.ops) {
			if (o.y > bottom || o.y + o.h < top) continue
			when (o) {
				is WikiLayout.Rect -> ctx.fill(o.x, o.y, o.x + o.w, o.y + o.h, o.argb)
				is WikiLayout.Text -> drawText(ctx, o, o.href != null && o.href == hoverHref)
				is WikiLayout.Image -> drawImage(ctx, o, theme)
				is WikiLayout.Toggle -> if (o === hover) ctx.fill(o.x, o.y, o.x + o.w, o.y + o.h, TOGGLE_HOVER)
			}
		}
		ctx.pose().popMatrix()
		ctx.disableScissor()
		if (hover != null) {
			if (hoverHref != null || hover is WikiLayout.Toggle) ctx.requestCursor(CursorTypes.POINTING_HAND)
			tooltipOf(hover)?.let { ctx.setComponentTooltipForNextFrame(font, it, mouseX, mouseY) }
		}
	}

	private fun drawText(ctx: GuiGraphicsExtractor, t: WikiLayout.Text, underline: Boolean) {
		val style = WikiFonts.style(t.st).let { if (underline) it.withUnderlined(true) else it }
		val comp = Component.literal(t.text).withStyle(style)
		ctx.pose().pushMatrix()
		ctx.pose().translate(t.x + t.sub, t.y.toFloat())
		if (t.st.scale != 1f) ctx.pose().scale(t.st.scale, t.st.scale)
		ctx.text(font, comp, 0, 0, t.argb, false)
		ctx.pose().popMatrix()
	}

	private fun drawImage(ctx: GuiGraphicsExtractor, o: WikiLayout.Image, theme: WikiLayout.Theme) {
		var ix = o.x; var iy = o.y; var iw = o.w; var ih = o.h
		if (o.slot) {
			ctx.fill(o.x, o.y, o.x + o.w, o.y + o.h, theme.slot)
			ctx.fill(o.x, o.y, o.x + o.w, o.y + 1, theme.slotDark)
			ctx.fill(o.x, o.y, o.x + 1, o.y + o.h, theme.slotDark)
			ctx.fill(o.x, o.y + o.h - 1, o.x + o.w, o.y + o.h, theme.slotLight)
			ctx.fill(o.x + o.w - 1, o.y, o.x + o.w, o.y + o.h, theme.slotLight)
			ix += 1; iy += 1; iw -= 2; ih -= 2
		}
		val img = o.img?.let(::frameOf) ?: return
		val tex = WikiImages.get(img.src)
		if (tex != null) {

			val s = minOf(iw.toFloat() / tex.w, ih.toFloat() / tex.h)
			val dw = maxOf(1, (tex.w * s).toInt()); val dh = maxOf(1, (tex.h * s).toInt())
			ctx.blit(RenderPipelines.GUI_TEXTURED, tex.tex.id, ix + (iw - dw) / 2, iy + (ih - dh) / 2, 0f, 0f, dw, dh, tex.w, tex.h, tex.w, tex.h)
		} else if ((o.img.main || img.main) && stack != null && !stack.isEmpty) {

			val side = minOf(iw, ih)
			val s = side / 16f
			ctx.pose().pushMatrix()
			ctx.pose().translate(ix + (iw - side) / 2f, iy + (ih - side) / 2f)
			ctx.pose().scale(s, s)
			ctx.fakeItem(stack, 0, 0)
			ctx.pose().popMatrix()
		}
		img.count?.takeIf { o.slot && it.isNotBlank() }?.let { c ->
			ctx.text(font, c, o.x + o.w - 1 - font.width(c), o.y + o.h - 9, 0xFFFFFFFF.toInt(), true)
		}
	}

	private fun tooltipOf(o: WikiLayout.Op): List<Component>? {
		val lines: List<Component>? = when (o) {
			is WikiLayout.Text -> o.tip?.split('\n')?.map { Component.literal(it) }
			is WikiLayout.Image -> o.img?.let(::frameOf)?.tip?.let { t -> listOf(Component.literal(t.title)) + t.lines.map { Component.literal(it) } }
			else -> null
		}
		return lines?.takeIf { it.isNotEmpty() }
	}

	private fun drawScrollbar(ctx: GuiGraphicsExtractor, theme: WikiLayout.Theme) {
		val (thumbTop, thumbH) = scroll.thumb(bodyH) ?: return
		ctx.fill(trackX, bodyY, trackX + BAR_W, bodyY + bodyH, theme.band)
		ctx.fill(trackX, bodyY + thumbTop, trackX + BAR_W, bodyY + thumbTop + thumbH, if (draggingThumb) theme.text else theme.muted)
	}

	private fun sectionRows(): List<Pair<WikiDoc.Block.Heading, Int>> = currentPage()?.sections.orEmpty()

	private fun sectionsBox(): Btn? {
		val b = button("sections") ?: return null
		val rows = sectionRows()
		if (rows.isEmpty()) return null
		val w = (rows.maxOf { (h, _) -> font.width(h.text) + (if (h.level > 2) 8 else 0) } + 12).coerceIn(80, panel.w - 8)
		val maxRows = ((panel.y + panel.h - (b.y + b.h + 2) - 4) / ROW_H).coerceAtLeast(1)
		val h = minOf(rows.size, maxRows) * ROW_H + 4
		val x = (b.x + b.w - w).coerceAtLeast(panel.x + 4)
		return Btn(x, b.y + b.h + 1, w, h, "")
	}

	private fun visibleSectionRows(): Int = sectionsBox()?.let { (it.h - 4) / ROW_H } ?: 0

	private fun drawSections(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
		val box = sectionsBox() ?: return
		val rows = sectionRows()
		ctx.nextStratum()
		ctx.fill(box.x - 1, box.y - 1, box.x + box.w + 1, box.y + box.h + 1, 0xFF000000.toInt())
		ctx.fill(box.x, box.y, box.x + box.w, box.y + box.h, MENU_BG)
		val visible = visibleSectionRows()
		sectionsTop = sectionsTop.coerceIn(0, (rows.size - visible).coerceAtLeast(0))
		for (i in 0 until visible) {
			val (h, _) = rows.getOrNull(sectionsTop + i) ?: break
			val y = box.y + 2 + i * ROW_H
			val hover = mx >= box.x && mx < box.x + box.w && my >= y && my < y + ROW_H
			if (hover) { ctx.fill(box.x, y, box.x + box.w, y + ROW_H, BTN_HOVER); ctx.requestCursor(CursorTypes.POINTING_HAND) }
			val indent = if (h.level > 2) 8 else 0
			val colour = if (h.level > 2) 0xFFBBBBBB.toInt() else 0xFFFFFFFF.toInt()
			val label = font.plainSubstrByWidth(h.text, box.w - 8 - indent)
			ctx.text(font, label, box.x + 4 + indent, y + 1, colour, false)
		}
	}

	override fun mouseClicked(click: MouseButtonEvent, twice: Boolean): Boolean =
		MenuPress.press { if (clickAt(click)) MenuPress.acted(); true }

	private fun clickAt(click: MouseButtonEvent): Boolean {
		val leftButton = click.button() == InputConstants.MOUSE_BUTTON_LEFT
		val mx = click.x(); val my = click.y()
		if (!leftButton) return false
		if (sectionsOpen) {
			val box = sectionsBox()
			if (box != null && box.hit(mx, my)) {
				val i = sectionsTop + ((my - box.y - 2) / ROW_H).toInt()
				sectionRows().getOrNull(i)?.let { (_, block) -> laid?.let { scroll.scrollTo(it.yOfBlock(block)) } }
			}
			sectionsOpen = false
			return true
		}
		for ((id, b) in buttons) if (visible(id) && b.hit(mx, my)) { press(id); return true }
		scroll.thumb(bodyH)?.let { (top, th) ->
			if (mx >= trackX && mx < trackX + BAR_W && my >= bodyY && my < bodyY + bodyH) {
				val rel = (my - bodyY).toInt()
				if (rel < top || rel >= top + th) scroll.dragThumbTo(rel - th / 2, bodyH)
				thumbGrab = rel - (scroll.thumb(bodyH)?.first ?: 0)
				draggingThumb = true
				return true
			}
		}
		if (footerBox().hit(mx, my)) { confirm(WikiAttribution.LICENCE_URL); return true }
		val l = laid
		if (l != null && view is View.Ready && mx >= bodyX && mx < bodyX + bodyW && my >= bodyY && my < bodyY + bodyH) {
			val lx = (mx - bodyX).toInt()
			val ly = (my - bodyY).toInt() + scroll.offset
			l.toggleAt(lx, ly)?.let { t ->
				layoutState = if (t.tab < 0) layoutState.flip(t.id) else layoutState.tab(t.id, t.tab)
				return true
			}
			l.hrefAt(lx, ly, ::frameOf)?.let { href -> follow(href); return true }
		}
		return false
	}

	private fun press(id: String) {
		when (id) {
			"close" -> onClose()
			"back" -> goBack()
			"sections" -> { sectionsOpen = !sectionsOpen; sectionsTop = 0 }
			"browser" -> confirm(browserUrl())
		}
	}

	private fun browserUrl(): String {
		val c = history.current
		return if (c != null) WikiHosts.pageUrl(c.title, c.anchor)
		else "https://${WikiHosts.HOST}/w/Special:Search?search=" + URLEncoder.encode(itemName, Charsets.UTF_8)
	}

	private fun confirm(url: String) {
		val uri = runCatching { URI(url) }.getOrNull() ?: return
		ConfirmLinkScreen.confirmLinkNow(this, uri)
	}

	private fun follow(href: String) {
		when (val k = WikiLinks.classify(href)) {
			is WikiLinks.Kind.Anchor -> jumpTo(k.anchor)
			is WikiLinks.Kind.External -> k.url?.let(::confirm)
			is WikiLinks.Kind.Internal -> {
				val here = currentPage()?.title ?: history.current?.title
				if (here != null && WikiTitles.normalise(here) == WikiTitles.normalise(k.title) && k.anchor != null) jumpTo(k.anchor)
				else navigate(k.title, k.anchor)
			}
		}
	}

	private fun jumpTo(anchor: String) {
		val p = currentPage() ?: return
		val l = laid ?: return
		p.indexOfAnchor(anchor)?.let { scroll.scrollTo(l.yOfBlock(it)) }
	}

	override fun mouseDragged(click: MouseButtonEvent, dx: Double, dy: Double): Boolean {
		if (draggingThumb) {
			scroll.dragThumbTo((click.y() - bodyY).toInt() - thumbGrab, bodyH)
			return true
		}
		return super.mouseDragged(click, dx, dy)
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean {
		draggingThumb = false
		return super.mouseReleased(click)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, sideways: Double, wheel: Double): Boolean {
		val overSections = sectionsOpen && sectionsBox()?.hit(mouseX, mouseY) == true
		if (overSections) {
			sectionsTop = (sectionsTop - wheel.toInt().coerceIn(-3, 3)).coerceIn(0, (sectionRows().size - visibleSectionRows()).coerceAtLeast(0))
			return true
		}
		scroll.wheel(wheel, WikiLayout.LINE_H)
		return true
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		val key = ItemWiki.TConfig.key
		if (keyArmed && key.isBound && key.matches(GenericInputAction.of(input), InputModifiers.of(input), atLeast = true)) {
			onClose()
			return true
		}
		if (sectionsOpen && input.key() == InputConstants.KEY_ESCAPE) { sectionsOpen = false; return true }
		when (input.key()) {
			InputConstants.KEY_PAGEUP -> scroll.pageUp()
			InputConstants.KEY_PAGEDOWN -> scroll.pageDown()
			InputConstants.KEY_HOME -> scroll.home()
			InputConstants.KEY_END -> scroll.end()
			InputConstants.KEY_UP -> scroll.scrollBy(-WikiLayout.LINE_H * 2)
			InputConstants.KEY_DOWN -> scroll.scrollBy(WikiLayout.LINE_H * 2)
			InputConstants.KEY_BACKSPACE -> goBack()
			else -> return super.keyPressed(input)
		}
		return true
	}

	override fun onClose() {
		loadGen++
		ItemWiki.hold.suppressUntilRelease()
		Client.screen = parent
	}

	override fun isPauseScreen(): Boolean = false

	private class LayoutKey(val page: WikiDoc.Page, val w: Int, val smooth: Boolean, val dark: Boolean, val state: WikiLayout.State) {
		override fun equals(other: Any?) = other is LayoutKey && other.page === page && other.w == w && other.smooth == smooth && other.dark == dark && other.state == state
		override fun hashCode() = System.identityHashCode(page) * 31 + w
	}

	private companion object {

		const val EXTRA_CHARS = "•·[]HideShowContents0123456789,.-+ coins?"
		val HOVERABLE: (WikiLayout.Op) -> Boolean = { o ->
			when (o) {
				is WikiLayout.Text -> o.href != null || o.tip != null
				is WikiLayout.Image -> o.href != null || o.img?.tip != null || o.img?.frames?.any { it.tip != null } == true
				is WikiLayout.Toggle -> true
				else -> false
			}
		}
		const val BAR_W = 4
		const val ROW_H = 11
		const val FRAME_MS = 2000L
		val DIM = 0x99000000.toInt()
		val HEADER_BG = 0xFF2B2F33.toInt()
		val BTN_BG = 0xFF454B52.toInt()
		val BTN_HOVER = 0xFF5F6B78.toInt()
		val MENU_BG = 0xF0202326.toInt()
		val TOGGLE_HOVER = 0x30FFFFFF
	}
}
