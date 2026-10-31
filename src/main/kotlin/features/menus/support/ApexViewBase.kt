// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.features.menus.ContainerGate
import dev.ashtonite365.apex.features.menus.MenuClicks
import dev.ashtonite365.apex.features.menus.MenuSession
import dev.ashtonite365.apex.features.menus.MenuChainRunner
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.bareText

abstract class ApexViewBase<M : Any>(val screen: AbstractContainerScreen<*>) : MenuView {
	protected var session: MenuSession? = null
	override fun attached(session: MenuSession) { this.session = session }

	protected fun showVanilla() { session?.setHidden(true) }
	override var x = 0; override var y = 0; override var w = 0; override var h = 0
	protected val font: Font get() = Client.font
	private val openedAt = System.currentTimeMillis()
	private var sig = Int.MIN_VALUE
	protected var model: M? = null; private set
	protected var stacks: Map<Int, ItemStack> = emptyMap(); private set
	private var fellBack = false

	protected class Area(val x: Int, val y: Int, val w: Int, val h: Int, val enabled: Boolean, val onClick: (button: Int) -> Unit) {
		fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
	}
	protected val areas = mutableListOf<Area>()

	abstract fun parse(title: String, slots: List<SlotText>): M?
	abstract fun draw(ctx: GuiGraphicsExtractor, m: M, mx: Int, my: Int)

	protected open val logTag: String get() = "[Apex] ${this::class.simpleName}"

	override fun layout(x: Int, y: Int, w: Int, h: Int) { this.x = x; this.y = y; this.w = w; this.h = h }

	private var changedAt = openedAt

	private fun refresh() {
		val s = MenuSlots.signature(screen)
		if (s == sig) return
		sig = s
		changedAt = System.currentTimeMillis()
		val (texts, st) = MenuSlots.snapshot(screen)
		stacks = st
		model = runCatching { parse(screen.title.bareText, texts) }.getOrNull()
		model?.let { ViewCache.put(this::class, it, stacks) }
	}

	companion object {
		fun shouldFallBack(unreadable: Boolean, nowMs: Long, changedAtMs: Long, chainRunning: Boolean) =
			unreadable && !chainRunning && nowMs - changedAtMs > ContainerGate.GRACE_MS
	}

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		refresh()
		areas.clear()
		val p = palette()
		MenuTheme.panel(ctx, x, y, w, h, p)
		val now = System.currentTimeMillis()
		var m = model
		if (m == null) {
			if (!fellBack && shouldFallBack(true, now, changedAt, MenuChainRunner.active)) { fellBack = true
				dev.ashtonite365.apex.Apex.logger.warn("$logTag: could not read the page; leaving it showing (no auto-click)")
				session?.fallback("could not read the page") }

			@Suppress("UNCHECKED_CAST")
			val cached = ViewCache.get(this::class, now) as Pair<M, Map<Int, ItemStack>>?
			if (cached == null) {
				val t = "Reading menu…"
				ctx.text(font, "§7$t", x + (w - font.width(t)) / 2, y + h / 2 - 4, MenuTheme.GREY)
				return
			}
			m = cached.first
			if (stacks.isEmpty()) stacks = cached.second
			drawingCached = true
		} else drawingCached = false

		try { draw(ctx, m, mouseX, mouseY) } catch (e: Exception) {
			areas.clear()
			if (!fellBack) { fellBack = true
				dev.ashtonite365.apex.Apex.logger.warn("$logTag: render failed: ${e.message}")
				session?.fallback("render failed") }
			return
		}
		if (drawingCached) areas.clear()
		if (MenuChainRunner.active) {
			ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xC0101014.toInt())
			val t = "Working… (click to cancel)"
			ctx.text(font, "§6$t", x + (w - font.width(t)) / 2, y + h / 2 - 4, MenuTheme.GOLD)
		}
	}

	protected var drawingCached = false; private set

	abstract fun palette(): MenuTheme.Palette

	private var clip: dev.ashtonite365.apex.features.menus.MenuBounds? = null

	protected fun beginClip(ctx: GuiGraphicsExtractor, view: dev.ashtonite365.apex.features.menus.MenuBounds) {
		ctx.enableScissor(view.x, view.y, view.x + view.w, view.y + view.h); clip = view
	}

	protected fun endClip(ctx: GuiGraphicsExtractor) { ctx.disableScissor(); clip = null }

	protected fun hovered(mx: Int, my: Int, bx: Int, by: Int, bw: Int, bh: Int) =
		ScrollClip.hit(mx, my, dev.ashtonite365.apex.features.menus.MenuBounds(bx, by, bw, bh), clip)

	private fun addArea(bx: Int, by: Int, bw: Int, bh: Int, enabled: Boolean, onClick: (Int) -> Unit) {
		val r = dev.ashtonite365.apex.features.menus.MenuBounds(bx, by, bw, bh)
		val v = clip?.let { ScrollClip.clip(r, it) ?: return } ?: r
		areas += Area(v.x, v.y, v.w, v.h, enabled, onClick)
	}

	protected fun button(ctx: GuiGraphicsExtractor, mx: Int, my: Int, bx: Int, by: Int, bw: Int, bh: Int, label: String,
	                     active: Boolean = false, enabled: Boolean = true, onClick: (Int) -> Unit) {
		val hover = hovered(mx, my, bx, by, bw, bh)
		MenuTheme.button(ctx, font, bx, by, bw, bh, MenuTheme.fit(label, bw - 6, font::width), MenuTheme.state(hover, active, enabled), palette = palette())
		addArea(bx, by, bw, bh, enabled, onClick)
	}

	protected fun area(bx: Int, by: Int, bw: Int, bh: Int, onClick: (Int) -> Unit) = addArea(bx, by, bw, bh, true, onClick)

	protected fun searchBox(ctx: GuiGraphicsExtractor, mx: Int, my: Int, search: MenuSearch, bx: Int, by: Int, bw: Int, hint: String): Int {
		MenuTheme.frame(ctx, bx, by, bx + bw, by + 14, MenuTheme.FILL, if (search.focused) palette().accent else MenuTheme.BORDER)
		val q = search.query
		var shown = q + if (search.focused) "_" else ""
		while (shown.isNotEmpty() && font.width(shown) > bw - 8) shown = shown.drop(1)
		ctx.text(font, if (q.isEmpty() && !search.focused) "§8$hint" else "§f$shown", bx + 4, by + 3, MenuTheme.WHITE)
		area(bx, by, bw, 14) { search.focused = true }
		if (q.isNotEmpty()) button(ctx, mx, my, bx + bw + 4, by, 14, 14, "x") { search.clear() }
		return 14
	}

	protected fun scrollKey(extra: String = ""): String =
		"menu:${this::class.java.name}|${screen.title.string}|$extra"

	protected open val scrollView: ScrollView? get() = null

	protected fun windowStillOpen(): Boolean {
		val menu = screen.menu
		if (Client.player?.containerMenu === menu) return true
		dev.ashtonite365.apex.Apex.logger.warn("[Apex] click on a closed window (${menu.containerId}); showing vanilla")
		session?.fallback("window changed"); return false
	}

	protected fun clickSlot(slot: Int, button: Int = 0) {
		val menu = screen.menu
		if (!windowStillOpen()) return
		val s = menu.slots.getOrNull(slot) ?: return
		MenuClicks.click(menu, s, if (button == 1) 1 else 0)
	}

	protected fun realSlotCell(slot: Int, fx: Int, fy: Int): Boolean {
		val s = session ?: return false
		if (screen.menu.slots.getOrNull(slot) == null) return false
		val cell = dev.ashtonite365.apex.features.menus.MenuBounds(fx, fy, 18, 18)
		clip?.let { if (ScrollClip.clip(cell, it) != cell) return false }
		s.realCells[slot] = InventoryPanel.cellItemPos(fx, fy).let { (ix, iy) -> toScreen(ix, iy) }
		s.cellScale = drawScale
		return true
	}

	protected fun standInSlotCell(slot: Int, ix: Int, iy: Int) {
		session?.standInCells?.put(slot, toScreen(ix, iy))
	}

	protected open val drawScale: Float get() = 1f
	protected open fun toScreen(lx: Int, ly: Int): Pair<Int, Int> = lx to ly
	protected open fun toLocal(mx: Double, my: Double): Pair<Double, Double> = mx to my

	protected fun inventoryPanel(ctx: GuiGraphicsExtractor, mx: Int, my: Int, ix: Int, iy: Int, k: Float = 1f) {
		val p = palette()
		ctx.text(font, "§7Inventory", ix, iy, MenuTheme.GREY)
		if (k != 1f && drawScale == 1f) {

			val s = session
			val n = screen.menu.slots.size
			for (row in 0..3) for (col in 0..8) {
				val (sx, sy, size) = InventoryPanel.scaledCell(row, col, ix, iy, k)
				val hov = MenuTheme.inside(mx, my, sx, sy, size, size)
				MenuTheme.frame(ctx, sx, sy, sx + size, sy + size, if (hov) p.fillHover else p.fill, if (hov) p.borderHover else p.border)
				if (s != null && n >= 36) {
					val idx = InventoryPanel.slotIndex(row, col, InventoryPanel.chestSlots(n))
					s.realCells[idx] = (sx + Math.round(k)) to (sy + Math.round(k)); s.cellScales[idx] = k
				}
			}
			return
		}
		if (drawScale == 1f) session?.panelOrigin = ix to iy
		else session?.let { s ->

			val n = screen.menu.slots.size
			if (n >= 36) for (row in 0..3) for (col in 0..8) {
				val (ax, ay) = InventoryPanel.itemPos(row, col, ix, iy)
				s.realCells[InventoryPanel.slotIndex(row, col, InventoryPanel.chestSlots(n))] = toScreen(ax, ay)
			}
			s.cellScale = drawScale
		}
		for (row in 0..3) for (col in 0..8) {
			val (ox, oy) = InventoryPanel.offset(row, col)
			val sx = ix + ox; val sy = iy + 10 + oy
			val hov = MenuTheme.inside(mx, my, sx, sy, 18, 18)
			MenuTheme.frame(ctx, sx, sy, sx + 18, sy + 18, if (hov) p.fillHover else p.fill, if (hov) p.borderHover else p.border)
		}
	}

	fun clickSlotFromPanel(slot: Int) = clickSlot(slot, 0)

	fun currentModel(): M? { refresh(); return model }

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean = dev.ashtonite365.apex.features.menus.MenuPress.press {
		if (MenuChainRunner.active) { MenuChainRunner.cancel("cancelled"); return@press true }
		val btn = MenuButtons.packetButton(click.button()) ?: return@press true
		val (lx, ly) = toLocal(click.x(), click.y())
		areas.lastOrNull { it.contains(lx, ly) }?.let { if (it.enabled) { dev.ashtonite365.apex.features.menus.MenuPress.acted(); it.onClick(btn) } }
		true
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean { scrollView?.scroll(amount); return true }
	override fun keyPressed(input: KeyEvent): Boolean {
		if (input.input() == InputConstants.KEY_ESCAPE) return interceptClose()
		return false
	}

	override fun interceptClose(): Boolean {
		if (MenuChainRunner.active) { MenuChainRunner.cancel("cancelled"); return true }
		return false
	}
	override fun charTyped(input: CharacterEvent) = true
}

object ViewCache {
	const val MAX_AGE_MS = 5000L
	private val map = mutableMapOf<kotlin.reflect.KClass<*>, Triple<Any, Map<Int, ItemStack>, Long>>()
	fun put(k: kotlin.reflect.KClass<*>, m: Any, stacks: Map<Int, ItemStack>) { map[k] = Triple(m, stacks, System.currentTimeMillis()) }
	fun get(k: kotlin.reflect.KClass<*>, now: Long): Pair<Any, Map<Int, ItemStack>>? =
		map[k]?.takeIf { now - it.third <= MAX_AGE_MS }?.let { it.first to it.second }
}

object MenuDebug {
	fun dump(title: String, containerId: Int, slots: List<SlotText>) = buildString {
		append("[ApexSlotDump] id=").append(containerId).append(" title=")
		append(MenuText.clean(title).map { if (it.code < 128) it.toString() else "\\u%04X".format(it.code) }.joinToString(""))
		for (t in slots) append("\n[ApexSlotDump]   slot=").append(t.slot).append(" name=").append(t.name.replace("§", "&"))
			.append(" lore=").append(t.lore.map { it.replace("§", "&") })
	}
}
