// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.keybindings.GenericInputAction
import dev.ashtonite365.apex.keybindings.InputModifiers
import dev.ashtonite365.apex.keybindings.SavedKeyBinding

data class Tile(val slot: Int, val label: String, val info: List<String> = emptyList(), val active: Boolean = false, val keyHint: String = "")

data class TileGroup(val name: String, val tiles: List<Tile>)

data class PageNav(val page: Int = 1, val pages: Int = 1, val prev: Int? = null, val next: Int? = null, val back: Int? = null, val close: Int? = null, val backLabel: String? = null)

object TileParse {
	private val FILLER = setOf("", " ", "Close", "Go Back", "Next Page", "Previous Page")

	fun isFiller(s: SlotText) = s.cleanName in FILLER || s.cleanName.isBlank()
	fun find(slots: List<SlotText>, name: String, max: Int) = slots.firstOrNull { it.slot < max && it.cleanName.equals(name, true) }?.slot
	fun nav(slots: List<SlotText>, max: Int, page: Pair<Int, Int>? = null): PageNav {
		val prev = find(slots, "Previous Page", max); val next = find(slots, "Next Page", max); val back = find(slots, "Go Back", max)
		val inferred = page ?: pageFromArrows(slots.firstOrNull { it.slot == prev }, slots.firstOrNull { it.slot == next })
		return PageNav(inferred?.first ?: 1, inferred?.second ?: 1, prev, next, back, find(slots, "Close", max),
			slots.firstOrNull { it.slot == back }?.cleanLore?.firstOrNull { it.isNotBlank() })
	}

	fun pageLabel(nav: PageNav): String = if (nav.pages > 1) " §7(${nav.page}/${nav.pages})" else ""
	private val ARROW_FRACTION = Regex("^\\((\\d+)/(\\d+)\\)$")
	private val ARROW_PAGE = Regex("^Page (\\d+)$")

	fun pageFromArrows(prev: SlotText?, next: SlotText?): Pair<Int, Int>? {
		fun n(s: SlotText?) = s?.cleanLore?.firstNotNullOfOrNull { ARROW_PAGE.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
		(next ?: prev)?.cleanLore?.firstNotNullOfOrNull { ARROW_FRACTION.matchEntire(it.trim()) }?.let { m ->
			val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); return a to maxOf(a, b) }
		val nx = n(next); val pv = n(prev)
		val cur = nx?.minus(1) ?: pv?.plus(1) ?: return null
		return cur to maxOf(cur, nx ?: cur)
	}

	private val PAGE = Regex("\\((\\d+)/(\\d+)\\)")
	fun page(title: String) = PAGE.find(title)?.let { (it.groupValues[1].toIntOrNull() ?: 1) to (it.groupValues[2].toIntOrNull() ?: 1) }

	fun lore(s: SlotText, r: Regex) = s.cleanLore.firstNotNullOfOrNull { l -> r.find(l)?.let { it.groupValues.getOrNull(1)?.takeIf(String::isNotEmpty) ?: it.value } }

	fun buttonTile(s: SlotText) = Tile(s.slot, s.cleanName, listOfNotNull(s.cleanLore.firstOrNull { it.isNotBlank() && !it.startsWith("/") }?.let { "§7$it" }))
	fun loreHas(s: SlotText, vararg words: String) = s.cleanLore.any { l -> words.any { l.contains(it, true) } }
}

object TileKeys {

	fun matches(k: SavedKeyBinding, input: KeyEvent) = k.isBound && k.matches(GenericInputAction.of(input), InputModifiers.of(input))
	fun label(k: SavedKeyBinding) = if (k.isBound) k.format().string else ""
}

abstract class TileViewBase<M : Any>(screen: AbstractContainerScreen<*>) : ApexViewBase<M>(screen) {
	companion object {
		const val TILE_W = 100
		const val TILE_H = 34
		const val GAP = 4

		fun columns(innerW: Int) = maxOf(1, (innerW + GAP) / (TILE_W + GAP))

		fun bodyHeight(groups: List<TileGroup>, cols: Int) = groups.sumOf { 12 + ((it.tiles.size + cols - 1) / cols) * (TILE_H + GAP) }
	}

	private val scroller = ScrollView { scrollKey() }
	override val scrollView get() = scroller

	abstract fun title(m: M): String
	abstract fun groups(m: M): List<TileGroup>
	abstract fun nav(m: M): PageNav

	open fun showsInventory(m: M): Boolean = false

	open fun inventoryScale(): Float = 1f

	open fun drawBody(ctx: GuiGraphicsExtractor, m: M, mx: Int, my: Int, top: Int, bottom: Int): Boolean = false

	open fun subtitle(m: M): String = ""

	protected open fun drawSubtitle(ctx: GuiGraphicsExtractor, sub: String, sx: Int, sy: Int, maxW: Int) {
		ctx.text(font, MenuTheme.fit(sub, maxW, font::width), sx, sy, MenuTheme.GREY)
	}

	protected open fun labelReserve(t: Tile, group: String): Int = 0
	protected open fun tileOverlay(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, t: Tile, group: String) {}
	protected open fun tileTooltip(t: Tile, group: String): List<net.minecraft.network.chat.Component>? = null
	private var hoveredTile: Pair<Tile, String>? = null

	override fun draw(ctx: GuiGraphicsExtractor, m: M, mx: Int, my: Int) {
		val p = palette()
		val nav = nav(m)
		val head = "§l" + title(m) + TileParse.pageLabel(nav)
		ctx.text(font, head, x + 6, y + 7, p.accent)
		val sub = subtitle(m)
		val vw = font.width("Vanilla") + 10
		if (sub.isNotEmpty()) drawSubtitle(ctx, sub, x + 12 + font.width(head), y + 7, w - font.width(head) - vw - 24)

		val inv = showsInventory(m)
		val k = inventoryScale()
		val top = y + 22; val bottom = y + h - 22 - (if (inv) InventoryPanel.scaledHeight(k) + 4 else 0)
		val innerW = w - 12
		val cols = columns(innerW)
		val drewBody = drawBody(ctx, m, mx, my, top, bottom)
		val groups = if (drewBody) emptyList() else groups(m)
		val view = MenuBounds(x + 1, top, w - 2, bottom - top)

		scroller.layout(view, bodyHeight(groups, cols) - if (groups.any { it.tiles.isNotEmpty() }) GAP else 0)
		var cy = top - scroller.offset
		hoveredTile = null
		beginClip(ctx, view)
		for (g in groups) {
			if (cy + 12 > top && cy < bottom) ctx.text(font, "§e${g.name} §8(${g.tiles.size})", x + 6, cy + 2, MenuTheme.WHITE)
			cy += 12
			g.tiles.forEachIndexed { i, t ->
				val tx = x + 6 + (i % cols) * (TILE_W + GAP)
				val ty = cy + (i / cols) * (TILE_H + GAP)
				if (ty + TILE_H > top && ty < bottom) drawTile(ctx, mx, my, tx, ty, t, g.name)
			}
			cy += ((g.tiles.size + cols - 1) / cols) * (TILE_H + GAP)
		}
		endClip(ctx)
		if (groups.isEmpty() && !drewBody) ctx.text(font, "§7Nothing here", x + 6, top + 4, MenuTheme.GREY)

		var fx = x + 4
		fun foot(label: String, slot: Int?) {
			if (slot == null) return
			val bw = font.width(label) + 12
			button(ctx, mx, my, fx, y + h - 18, bw, 14, label) { b -> clickSlot(slot, b) }
			fx += bw + 4
		}
		foot("< Prev", nav.prev); foot(nav.backLabel?.takeIf { it.startsWith("To ", true) }?.let { "< " + it.substring(3) } ?: "Back", nav.back); foot("Close", nav.close)
		nav.next?.let { n -> val bw = font.width("Next >") + 12; button(ctx, mx, my, x + w - bw - 4, y + h - 18, bw, 14, "Next >") { b -> clickSlot(n, b) } }
		if (inv) inventoryPanel(ctx, mx, my, x + (w - 9 * Math.round(18 * k)) / 2, bottom + 2, k)
		hoveredTile?.let { (t, g) -> tileTooltip(t, g)?.takeIf { it.isNotEmpty() }?.let { ctx.setComponentTooltipForNextFrame(font, it, mx, my) } }
	}

	private fun drawTile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, t: Tile, group: String) {
		val p = palette()
		val hover = hovered(mx, my, tx, ty, TILE_W, TILE_H)
		MenuTheme.frame(ctx, tx, ty, tx + TILE_W, ty + TILE_H, if (t.active) p.fillActive else if (hover) p.fillHover else p.fill,
			if (t.active) p.accent else if (hover) p.borderHover else p.border)
		stacks[t.slot]?.let { ctx.item(it, tx + 3, ty + 3) }
		ctx.text(font, MenuTheme.fit(t.label, TILE_W - 24 - labelReserve(t, group), font::width), tx + 21, ty + 3, MenuTheme.WHITE)
		t.info.take(2).forEachIndexed { i, l -> ctx.text(font, MenuTheme.fit(l, TILE_W - (if (i == 0) 24 else 6), font::width), if (i == 0) tx + 21 else tx + 3, ty + 13 + i * 10, MenuTheme.GREY) }
		if (t.keyHint.isNotEmpty()) ctx.text(font, "§8[${t.keyHint}]", tx + TILE_W - font.width("[${t.keyHint}]") - 2, ty + TILE_H - 10, MenuTheme.DIM)

		area(tx, ty, TILE_W, TILE_H) { b -> clickSlot(t.slot, b) }
		tileOverlay(ctx, mx, my, tx, ty, t, group)
		if (hover) hoveredTile = t to group
	}

	protected fun routeKey(input: KeyEvent, bindings: List<Pair<SavedKeyBinding, Int?>>): Boolean {
		val slot = bindings.firstOrNull { TileKeys.matches(it.first, input) } ?: return false
		slot.second?.let { clickSlot(it, 0) }
		return true
	}
	override fun keyPressed(input: KeyEvent): Boolean = super.keyPressed(input)
}

data class DynamicPage(val title: String, val items: List<SlotText>, val nav: PageNav, val header: SlotText?)

object DynamicParse {

	fun parse(title: String, slots: List<SlotText>, titleRegex: Regex, chestSize: Int, allowEmpty: Boolean = false): DynamicPage? {
		val t = MenuText.clean(title)
		if (!titleRegex.containsMatchIn(t)) return null
		val chest = slots.filter { it.slot < chestSize }
		val nav = TileParse.nav(chest, chestSize, TileParse.page(t))
		val bare = t.replace(Regex("\\s*\\(\\d+/\\d+\\)\\s*"), "").trim()
		val leaf = bare.substringAfterLast('➜').trim()
		val header = chest.firstOrNull { it.cleanName.equals(bare, true) } ?: chest.firstOrNull { leaf.isNotEmpty() && it.cleanName.equals(leaf, true) }
		val items = chest.filter { !TileParse.isFiller(it) && it !== header }
		if (items.isEmpty() && header == null && nav.close == null && nav.back == null && !allowEmpty) return null
		return DynamicPage(t, items, nav, header)
	}

	fun groupBy(items: List<SlotText>, key: (SlotText) -> String): List<Pair<String, List<SlotText>>> =
		items.groupBy(key).toList()

	fun objectives(s: SlotText): Pair<Int, Int> {
		val l = s.cleanLore
		val done = l.count { it.startsWith("✔") }
		return done to (done + l.count { it.startsWith("✖") })
	}

	fun nextObjective(s: SlotText) = s.cleanLore.firstOrNull { it.startsWith("✖") }?.removePrefix("✖")?.trim()
	private val PCT = Regex("(\\d+(?:\\.\\d+)?)%")
	private val FRACTION = Regex("^([\\d,.]+[kKmMbB]?/[\\d,.]+[kKmMbB]?)$")

	fun progressPct(s: SlotText) = s.cleanLore.firstOrNull { it.contains("Progress", true) }?.let { PCT.find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

	fun fraction(s: SlotText) = s.cleanLore.firstNotNullOfOrNull { FRACTION.matchEntire(it)?.groupValues?.get(1) }

	fun action(s: SlotText) = s.cleanLore.lastOrNull { it.startsWith("Click", true) || it.startsWith("Talk to", true) }
}
