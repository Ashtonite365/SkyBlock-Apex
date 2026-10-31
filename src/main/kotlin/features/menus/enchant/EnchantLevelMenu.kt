// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.enchant

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.minion.MinionViewBase
import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class EnchantLevel(
	val slot: Int, val level: Int, val name: String,
	val description: String, val fullCost: Int?, val cost: Int?, val affordable: Boolean?,

	val notes: List<String>,
	val state: State,
) {
	enum class State { CURRENT, LOWER, AVAILABLE }
}

data class EnchantLevelModel(
	val enchant: String,
	val itemSlot: Int?, val itemName: String?,
	val levels: List<EnchantLevel>,

	val current: Int?,
	val guideSlot: Int?, val backSlot: Int?, val backLore: String?,
) {
	val maxLevel get() = levels.maxOf { it.level }

	val complete get() = current != null && current >= maxLevel
}

object EnchantLevelParser {

	val TITLE = Regex("^Enchant Item\\s*➜\\s*\\S.*$", RegexOption.IGNORE_CASE)
	private val PRESENT = Regex("already present,? and can be removed|click to remove", RegexOption.IGNORE_CASE)
	private val HIGHER = Regex("higher level already present", RegexOption.IGNORE_CASE)

	fun paragraphs(lore: List<String>): List<String> {
		val out = mutableListOf<String>(); var cur = mutableListOf<String>()
		for (l in lore.map(MenuText::clean)) {
			if (l.isBlank()) { if (cur.isNotEmpty()) { out += cur.joinToString(" "); cur = mutableListOf() } }
			else cur += l.trim()
		}
		if (cur.isNotEmpty()) out += cur.joinToString(" ")
		return out.map { it.replace(Regex("\\s{2,}"), " ").trim() }
	}

	fun itemLevel(enchant: String, itemLore: List<String>): Int? = itemLore.map(MenuText::clean)
		.flatMap { it.split(", ") }.firstNotNullOfOrNull { l -> EnchantLabel.fromText(l.trim())?.takeIf { it.name.equals(enchant, true) }?.level }

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): EnchantLevelModel? {
		val t = MenuText.clean(title).trim()
		if (!TITLE.matches(t)) return null
		val tail = t.substringAfter('➜').trim()
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }

		val books = chest.mapNotNull { s ->
			if (s.cleanLore.none { it.trim().equals("Cost", true) }) return@mapNotNull null
			EnchantLabel.levelOption(s.cleanName, s.lore)?.takeIf { it.label.level != null }?.let { s to it }
		}
		if (books.isEmpty()) return null
		val names = books.map { it.second.label.name }.distinctBy { it.lowercase() }
		if (names.size != 1) return null
		val enchant = names.single()

		if (!enchant.equals(tail, true) && !(MenuTitles.maybeCut(t) && tail.isNotEmpty() && enchant.startsWith(tail, true))) return null
		val guide = chest.firstOrNull { it.cleanName.contains("Enchantment Guide", true) }
		val back = chest.firstOrNull { it.cleanName.equals("Go Back", true) }
		val close = chest.firstOrNull { it.cleanName.equals("Close", true) }
		val info = chest.filter { it.cleanName.equals("Enchant Item", true) }
		val known = books.map { it.first.slot }.toSet() + setOfNotNull(guide?.slot, back?.slot, close?.slot) + info.map { it.slot }
		val item = chest.firstOrNull { it.slot !in known }
		val bookCurrent = books.firstOrNull { (s, _) -> s.cleanLore.any { PRESENT.containsMatchIn(it) } }?.second?.label?.level
		val current = bookCurrent ?: item?.let { itemLevel(enchant, it.lore) }
		val levels = books.map { (s, o) ->
			val lvl = o.label.level!!
			val paras = paragraphs(s.lore)
			val costAt = paras.indexOfFirst { it.startsWith("Cost", true) }
			val desc = paras.getOrNull(0)?.takeIf { costAt != 0 } ?: ""
			val notes = paras.filterIndexed { i, _ -> i != costAt && !(i == 0 && desc.isNotEmpty()) }
			val state = when {
				current != null && lvl == current -> EnchantLevel.State.CURRENT
				current != null && lvl < current || s.cleanLore.any { HIGHER.containsMatchIn(it) } -> EnchantLevel.State.LOWER
				else -> EnchantLevel.State.AVAILABLE
			}
			EnchantLevel(s.slot, lvl, o.label.text, desc, o.fullCost, o.cost, o.affordable, notes, state)
		}.sortedBy { it.level }
		return EnchantLevelModel(enchant, item?.slot, item?.cleanName, levels, current, guide?.slot, back?.slot,
			back?.cleanLore?.firstOrNull { it.isNotBlank() })
	}
}

@Config
object EnchantLevelMenuSettings : MenuSettings("enchant-level") {
	val reset by button("reset") { resetToDefaults() }
}

object EnchantLevelMenu : CustomMenu {
	override val id = "enchant-level"
	override val displayName = "Enchant Item Levels"
	override val title = EnchantLevelParser.TITLE
	override val settings get() = EnchantLevelMenuSettings
	override val defaultSize = 400 to 260
	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) EnchantLevelView(screen) else null
}

class EnchantLevelView(screen: AbstractContainerScreen<*>) : MinionViewBase<EnchantLevelModel>(screen), MenuView {
	override fun palette() = EnchantLevelMenuSettings.customisation.palette
	private val chestSize get() = screen.menu.slots.size - 36
	private val scroller = ScrollView { scrollKey() }
	override val scrollView get() = scroller

	override fun parse(title: String, slots: List<SlotText>) = EnchantLevelParser.parse(title, slots, chestSize)

	private fun wrap(s: String, width: Int) = EnchantLabel.wrap(s, width) { font.width(it) }

	companion object {

		fun tag(m: EnchantLevelModel, l: EnchantLevel): String? = when (l.state) {
			EnchantLevel.State.CURRENT -> if (m.complete) "§a✔ Current · complete" else "§a● Current"
			EnchantLevel.State.LOWER -> "§8lower level"
			EnchantLevel.State.AVAILABLE -> null
		}

		fun costText(l: EnchantLevel): String? {
			val c = l.cost ?: return null
			val col = when (l.affordable) { true -> "§a"; false -> "§c"; null -> "§b" }
			return col + (if (l.fullCost != null && l.fullCost != c) "§m${l.fullCost}§r$col $c" else "$c") + " Exp Levels" +
				when (l.affordable) { true -> " ✔"; false -> " ✖"; null -> "" }
		}
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: EnchantLevelModel, mx: Int, my: Int) {
		val p = palette(); val pad = 6
		val vw = font.width("Vanilla") + 10

		var hy = y + 7
		for (line in wrap("§d§lEnchant Item ➜ ${m.enchant}", w - vw - 2 * pad - 6)) { ctx.text(font, line, x + pad, hy, MenuTheme.WHITE); hy += 10 }
		val top = maxOf(y + 22, hy + 2)

		val lw = 118; val ix = x + pad
		var ly = top + 2
		var tipStack: net.minecraft.world.item.ItemStack? = null
		m.itemSlot?.let { s ->
			MenuTheme.frame(ctx, ix, ly, ix + 18, ly + 18, MenuTheme.FILL, p.accent)
			val st = live(s)
			if (!st.isEmpty) { ctx.item(st, ix + 1, ly + 1); if (MenuTheme.inside(mx, my, ix, ly, 18, 18)) tipStack = st }
			ly += 22
			m.itemName?.let { n -> for (line in wrap("§f$n", lw)) { ctx.text(font, line, ix, ly, MenuTheme.WHITE); ly += 10 } }
		}
		ly += 4
		val cur = m.current?.let { "§7Current: §a${m.enchant} ${EnchantLabel.roman(it)}" } ?: "§7Current: §8not on the item"
		for (line in wrap(cur, lw)) { ctx.text(font, line, ix, ly, MenuTheme.GREY); ly += 10 }
		ctx.text(font, "§7Max here: §f${EnchantLabel.roman(m.maxLevel)}", ix, ly, MenuTheme.GREY); ly += 10
		if (m.complete) { ly += 2; for (line in wrap("§a§l✔ Complete §7(max level)", lw)) { ctx.text(font, line, ix, ly, MenuTheme.WHITE); ly += 10 } }

		val rx = x + pad + lw + 6; val rw = x + w - pad - rx
		val bottom = y + h - pad - 18
		MenuTheme.panel(ctx, rx, top, rw, bottom - top, p)
		val textW = rw - 34
		val rows = m.levels.map { l ->
			val head = "§f${l.name}" + (tag(m, l)?.let { " $it" } ?: "")
			val lines = wrap(head, textW) + listOfNotNull(costText(l)).flatMap { wrap(it, textW) } +
				(if (l.description.isNotEmpty()) wrap("§7${l.description}", textW) else emptyList()) +
				l.notes.flatMap { n -> wrap("§8$n", textW) }
			l to lines
		}
		val rowH = rows.map { maxOf(22, it.second.size * 10 + 6) }
		val view = MenuBounds(rx + 1, top + 1, rw - 2, maxOf(0, bottom - top - 2))
		scroller.layout(view, rowH.sum() + 2 * rows.size + 2)
		beginClip(ctx, view)
		var ry = top + 3 - scroller.offset
		rows.forEachIndexed { i, (l, lines) ->
			val hgt = rowH[i]
			if (ry + hgt > view.y && ry < view.y + view.h) {
				val hov = hovered(mx, my, rx + 3, ry, rw - 6, hgt)
				val border = when (l.state) { EnchantLevel.State.CURRENT -> 0xFF55FF55.toInt(); EnchantLevel.State.LOWER -> MenuTheme.DIM; else -> p.border }
				MenuTheme.frame(ctx, rx + 3, ry, rx + rw - 3, ry + hgt, if (hov) p.fillHover else p.fill, border)
				stacks[l.slot]?.let { ctx.item(it, rx + 6, ry + 3) }
				lines.forEachIndexed { j, s -> ctx.text(font, s, rx + 28, ry + 4 + j * 10, MenuTheme.WHITE) }
				if (l.state == EnchantLevel.State.LOWER) ctx.fill(rx + 4, ry + 1, rx + rw - 4, ry + hgt - 1, 0x55000000)

				area(rx + 3, ry, rw - 6, hgt) { b -> clickSlot(l.slot, b) }
			}
			ry += hgt + 2
		}
		endClip(ctx)

		var bx = x + pad; val by = bottom + 3
		m.backSlot?.let { s -> val l = "‹ Go Back"; val bw = font.width(l) + 12
			button(ctx, mx, my, bx, by, bw, 14, l) { b -> clickSlot(s, b) }; bx += bw + 4 }
		m.guideSlot?.let { s -> val l = "Enchant Guide"; val bw = font.width(l) + 12
			button(ctx, mx, my, bx, by, bw, 14, l) { b -> clickSlot(s, b) } }
		tipStack?.let { if (screen.menu.carried.isEmpty) ctx.setTooltipForNextFrame(font, it, mx, my) }
		finish(ctx, mx, my)
	}
}
