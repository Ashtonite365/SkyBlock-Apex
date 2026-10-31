// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.enchant

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.collections.LoreParse
import dev.ashtonite365.apex.features.menus.minion.MinionViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class EnchantOption(val slot: Int, val name: String, val base: String, val level: Int?, val applied: Boolean, val cost: Int?, val lore: List<String>)

data class EnchantModel(

	val itemSlot: Int?,
	val options: List<EnchantOption>,

	val hints: List<String>,
	val sortSlot: Int?,
	val sort: String?,
	val sortChoices: List<String>,
	val guideSlot: Int?,
	val closeSlot: Int?,
	val backSlot: Int?,
	val others: List<Int>,
	val nextSlot: Int? = null,
	val prevSlot: Int? = null,

	val tiles: List<EnchantTile> = emptyList(),
) {

	val missingCount get() = EnchantConflicts.missingCount(tiles)
}

object EnchantParser {
	val TITLE = Regex("^Enchant Item$", RegexOption.IGNORE_CASE)
	private val COST = Regex("(\\d[\\d,]*)\\s*(?:Exp(?:erience)?\\s*)?(?:Levels?|XP|Exp)", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>, chestSize: Int, emptySlots: Set<Int>, bookSlots: Set<Int>): EnchantModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		if (chest.isEmpty()) return null
		fun named(n: String) = chest.firstOrNull { it.cleanName.equals(n, true) }?.slot
		val sortS = chest.firstOrNull { it.cleanName.equals("Sort", true) }
		val guide = chest.firstOrNull { it.cleanName.contains("Enchantment Guide", true) }?.slot
		val close = named("Close"); val back = named("Go Back")
		val next = named("Next Page"); val prev = named("Previous Page")
		val hintsS = chest.filter { it.cleanName.equals("Enchant Item", true) }
		val known = setOfNotNull(sortS?.slot, guide, close, back, next, prev) + hintsS.map { it.slot }

		val itemSlot = chest.firstOrNull { it.slot !in known && it.slot !in bookSlots && !looksLikeOption(it) }?.slot
			?: emptySlots.filter { it < chestSize }.minOrNull()
		val options = chest.filter { it.slot !in known && it.slot != itemSlot && (it.slot in bookSlots || looksLikeOption(it)) }.map(::option)
		val sl = sortS?.cleanLore.orEmpty(); val sel = sl.indexOfFirst { it.startsWith("▶") }
		val sortChoices = if (sel < 0) emptyList() else {
			var a = sel; while (a > 0 && sl[a - 1].isNotBlank()) a--
			var b = sel; while (b + 1 < sl.size && sl[b + 1].isNotBlank()) b++
			sl.subList(a, b + 1).map { it.removePrefix("▶").trim() }
		}
		val others = chest.map { it.slot }.filter { it !in known && it != itemSlot && options.none { o -> o.slot == it } }
		return EnchantModel(itemSlot, options, hintsS.map { it.cleanLore.joinToString(" ") }.filter { it.isNotBlank() },
			sortS?.slot, sortS?.cleanLore?.firstOrNull { it.startsWith("▶") }?.removePrefix("▶")?.trim(), sortChoices, guide, close, back, others,
			next, prev)
	}

	fun withConflicts(m: EnchantModel, slots: List<SlotText>, pools: List<List<String>>, names: Map<String, String> = emptyMap()): EnchantModel {
		val itemLore = m.itemSlot?.let { s -> slots.firstOrNull { it.slot == s }?.cleanLore }.orEmpty()
		return m.copy(tiles = EnchantConflicts.tiles(m.options, itemLore, pools, names))
	}

	private fun statusLine(s: SlotText) = s.cleanLore.firstOrNull { l -> l.startsWith(s.cleanName, true) && l.length > s.cleanName.length &&
		l.substring(s.cleanName.length).trim().let { r -> r == "✖" || r == "✔" || LoreParse.roman(r) != null || r.toIntOrNull() != null ||

			r.endsWith("✔") && r.dropLast(1).trim().let { lv -> LoreParse.roman(lv) != null || lv.toIntOrNull() != null } } }

	private fun looksLikeOption(s: SlotText) = statusLine(s) != null || s.cleanLore.any { COST.containsMatchIn(it) && it.contains("cost", true) || it.contains("click to enchant", true) || it.contains("click to remove", true) || it.contains("applied", true) }

	fun option(s: SlotText): EnchantOption {
		val status = statusLine(s)?.substring(s.cleanName.length)?.trim()
		if (status != null) {
			val lv = status.removeSuffix("✔").trim()
			val lvl = lv.toIntOrNull() ?: LoreParse.roman(lv)
			return EnchantOption(s.slot, s.cleanName, s.cleanName, lvl, status != "✖", null, s.cleanLore)
		}
		val (base, lvl) = LoreParse.splitTier(s.cleanName)
		val low = s.cleanLore.joinToString("\n").lowercase()
		val applied = "click to remove" in low || "already applied" in low || "currently applied" in low || Regex("(?<!not )\\bapplied\\b").containsMatchIn(low) && "not applied" !in low

		val cost = s.cleanLore.firstNotNullOfOrNull { COST.find(it) }?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
		return EnchantOption(s.slot, s.cleanName, base, lvl, applied, cost, s.cleanLore)
	}
}

@Config
object EnchantMenuSettings : MenuSettings("enchant", panels = listOf("inventory")) {
	val reset by button("reset") { resetToDefaults() }
}

object EnchantMenu : CustomMenu {
	override val id = "enchant"
	override val displayName = "Enchant Item"
	override val title = EnchantParser.TITLE
	override val settings get() = EnchantMenuSettings
	override val defaultSize = 400 to 270
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) EnchantView(screen) else null
}

class EnchantView(screen: AbstractContainerScreen<*>) : MinionViewBase<EnchantModel>(screen), MenuView {
	private val c get() = EnchantMenuSettings.customisation
	override fun palette() = c.palette
	private val chestSize get() = screen.menu.slots.size - 36

	private val scroller = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey() }

	override fun parse(title: String, slots: List<SlotText>): EnchantModel? {
		val chest = screen.menu.slots.take(chestSize)
		val empty = chest.filter { it.item.isEmpty }.map { it.index }.toSet()
		val books = chest.filter { !it.item.isEmpty && BuiltInRegistries.ITEM.getKey(it.item.item).path == "enchanted_book" }.map { it.index }.toSet()
		val m = EnchantParser.parse(title, slots, chestSize, empty, books) ?: return null

		return EnchantParser.withConflicts(m, slots, EnchantConflicts.livePools(), EnchantConflicts.liveNames())
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: EnchantModel, mx: Int, my: Int) {
		val p = palette(); val pad = 6
		ctx.text(font, "§d§lEnchant Item", x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val ix = x + pad + 10; val iy = y + 34
		ctx.text(font, "§7Item", ix, iy - 10, MenuTheme.GREY)
		m.itemSlot?.let { tile(ctx, mx, my, ix, iy, it, highlight = p.accent, realItem = true) }
		var hy = iy + 26
		for (h in m.hints.distinct()) for (line in wrap(h, 110)) { ctx.text(font, "§8$line", x + pad, hy, MenuTheme.DIM); hy += 10 }

		val lx = x + pad + 124; val lw = x + w - pad - lx
		val showInv = c.shows("inventory")
		val bottom = y + h - pad - 18 - (if (showInv) 4 * 18 + 16 else 0)
		MenuTheme.panel(ctx, lx, y + 22, lw, bottom - y - 22, p)

		val groups = EnchantConflicts.groups(m.tiles, m.sort)
		val rowH = 20
		val rows = groups.sumOf { 1 + it.second.size }

		val view = dev.ashtonite365.apex.features.menus.MenuBounds(lx + 1, y + 23, lw - 2, maxOf(0, bottom - y - 24))
		scroller.layout(view, if (rows == 0) 0 else 3 + rows * rowH - 2)
		var r = 0; var ry: Int
		var tip: List<String>? = null
		if (groups.isEmpty()) ctx.text(font, MenuTheme.fit("§8Place an item to see its enchantments", lw - 12, font::width), lx + 6, y + 30, MenuTheme.DIM)
		fun vis(ry: Int) = ry + rowH > view.y && ry < view.y + view.h
		beginClip(ctx, view)
		for ((label, opts) in groups) {
			ry = y + 26 + r * rowH - scroller.offset
			if (vis(ry)) ctx.text(font, (if (label == "Conflicts") "§8" else "§e") + "$label §8(${opts.size})", lx + 6, ry + 6, MenuTheme.GOLD)
			r++
			for (t in opts) {
				val o = t.option
				ry = y + 26 + r * rowH - scroller.offset
				if (vis(ry)) {
					val hov = hovered(mx, my, lx + 4, ry, lw - 8, rowH - 2)
					val dim = t.conflictsWith != null
					MenuTheme.frame(ctx, lx + 4, ry, lx + lw - 4, ry + rowH - 2, if (hov) p.fillHover else p.fill, if (t.applied) 0xFF55FF55.toInt() else p.border)
					stacks[o.slot]?.let { ctx.item(it, lx + 6, ry + 1) }
					val lv = o.level?.let { " §a" + dev.ashtonite365.apex.features.inventory.EnchantPoolLogic.roman(it) } ?: ""
					val label = if (dim) "§8${o.name} §8(conflicts with ${t.conflictsWith})" else "§f${o.name}$lv"
					ctx.text(font, MenuTheme.fit(label, lw - 80, font::width), lx + 26, ry + 5, MenuTheme.WHITE)
					o.cost?.let { val c = (if (dim) "§8" else "§b") + "$it lvl"; ctx.text(font, c, lx + lw - 8 - font.width(c), ry + 5, MenuTheme.WHITE) }
					if (dim) ctx.fill(lx + 5, ry + 1, lx + lw - 5, ry + rowH - 3, 0x88000000.toInt())

					area(lx + 4, ry, lw - 8, rowH - 2) { b -> clickSlot(o.slot, b) }
					if (hov) tip = listOf("§f${o.name}") + (if (dim) listOf("§cConflicts with ${t.conflictsWith}: not missing") else emptyList()) + o.lore.map { "§7$it" }
				}
				r++
			}
		}
		endClip(ctx)

		var bx = x + pad; val by = bottom + 2
		m.backSlot?.let { s -> button(ctx, mx, my, bx, by, 60, 14, "‹ Go Back") { b -> clickSlot(s, b) }; bx += 63 }
		m.sortSlot?.let { s -> val l = "Sort: " + (m.sort ?: "?"); val bw = minOf(font.width(l) + 12, 170)
			button(ctx, mx, my, bx, by, bw, 14, l) { b -> clickSlot(s, b) }; bx += bw + 3 }
		m.guideSlot?.let { s -> button(ctx, mx, my, bx, by, 70, 14, "Guide") { b -> clickSlot(s, b) }; bx += 73 }
		m.prevSlot?.let { s -> button(ctx, mx, my, bx, by, 46, 14, "‹ Prev") { b -> clickSlot(s, b) }; bx += 49 }
		m.nextSlot?.let { s -> button(ctx, mx, my, bx, by, 46, 14, "Next ›") { b -> clickSlot(s, b) }; bx += 49 }
		m.closeSlot?.let { s -> button(ctx, mx, my, bx, by, 50, 14, "Close") { b -> clickSlot(s, b) } }

		if (showInv) inventory(ctx, mx, my, x + (w - 9 * 18) / 2, y + h - pad - 4 * 18 - 4, chestSize)
		tip?.let { t -> ctx.setComponentTooltipForNextFrame(font, t.map { Component.literal(it) }, mx, my) }
		finish(ctx, mx, my)
	}

	private fun wrap(s: String, width: Int): List<String> {
		val out = mutableListOf<String>(); var cur = ""
		for (word in s.split(' ')) { val n = if (cur.isEmpty()) word else "$cur $word"; if (font.width(n) > width && cur.isNotEmpty()) { out += cur; cur = word } else cur = n }
		if (cur.isNotEmpty()) out += cur
		return out
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean { scroller.scroll(amount); return true }
}
