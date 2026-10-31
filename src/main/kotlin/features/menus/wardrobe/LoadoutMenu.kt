// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.wardrobe

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.PageNav
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.TileKeys
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.MenuTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds
import dev.ashtonite365.apex.util.data.Config

data class Loadout(val index: Int, val slot: Int, val name: String, val active: Boolean, val empty: Boolean, val info: List<String>, val locked: Boolean = false)

data class LoadoutModel(val loadouts: List<Loadout>, val nav: PageNav, val current: List<SlotText> = emptyList())

object LoadoutParser {

	val TITLE = Regex("^(?:\\(\\d+/\\d+\\) )?Loadouts?$", RegexOption.IGNORE_CASE)

	fun parse(title: String, slots: List<SlotText>): LoadoutModel? {
		val t = MenuText.clean(title)
		if (!TITLE.matches(t)) return null
		val page = LoadoutKeybinds.Logic.page(t) ?: (1 to 1)
		val by = slots.associateBy { it.slot }
		val loadouts = LoadoutKeybinds.Logic.loadoutSlots.withIndex().mapNotNull { (i, sl) ->
			val s = by[sl]?.takeUnless(TileParse::isFiller) ?: return@mapNotNull null
			val active = TileParse.loreHas(s, "currently equipped", "currently selected", "Selected!", "Active!") ||
				s.cleanName.contains("(Equipped)", true) || s.cleanName.contains("(Selected)", true)
			val locked = s.cleanName.endsWith(" Locked", true)
			val empty = !locked && (TileParse.loreHas(s, "Click to create", "must customize") || s.cleanName.contains("Empty", true))
			val set = s.cleanLore.filter { it.contains(": ") && !it.endsWith(": None") }
			val info = if (locked) listOf("Locked") else if (empty) listOf("Not set up") else set.map { it.substringAfter(": ") }.take(2)
			Loadout(i, sl, s.cleanName.removeSuffix(" Locked"), active, empty, info, locked)
		}
		if (loadouts.isEmpty()) return null
		val nav = TileParse.nav(slots, 54, page).let { n ->
			n.copy(prev = if (page.first > 1) LoadoutKeybinds.Logic.PREVIOUS else null, next = if (page.first < page.second) LoadoutKeybinds.Logic.NEXT else null)
		}
		val loadoutSlots = LoadoutKeybinds.Logic.loadoutSlots.toSet()
		val current = slots.filter { it.slot < 54 && it.slot % 9 in 0..4 && it.slot / 9 in 1..4 && it.slot !in loadoutSlots && !TileParse.isFiller(it) }
		return LoadoutModel(loadouts, nav, current)
	}

	fun keySlot(m: LoadoutModel, k: Int): Int? = LoadoutKeybinds.Logic.loadoutSlots.getOrNull(k)?.takeIf { s -> m.loadouts.any { it.slot == s } }
}

@Config
object LoadoutSettings : MenuSettings("loadouts") {

	val showLocked by toggle("show-locked") { true }
	val showCurrent by toggle("show-current") { true }
	val reset by button("reset") { resetToDefaults() }
}

object LoadoutMenu : CustomMenu {
	override val id = "loadouts"
	override val displayName = "Loadouts"
	override val title = LoadoutParser.TITLE
	override val settings get() = LoadoutSettings

	override val defaultSize = 420 to 222

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = LoadoutView(screen)
}

class LoadoutView(screen: AbstractContainerScreen<*>) : ApexViewBase<LoadoutPage>(screen) {
	private var scroll by dev.ashtonite365.apex.util.ScrollMemory.int(settle = true) { scrollKey() }
	private var maxScroll = 0
	private val logged = HashSet<String>()
	override fun palette() = LoadoutSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = LoadoutPageParser.parse(title, slots) { msg ->
		if (logged.add(msg)) dev.ashtonite365.apex.Apex.logger.info(msg)
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: LoadoutPage, mx: Int, my: Int) {
		val p = palette()
		val lay = LoadoutLayout(LRect(x, y, w, h))
		var tip: net.minecraft.world.item.ItemStack? = null
		var textTip: List<String>? = null

		val head = "§lLoadouts" + TileParse.pageLabel(m.nav)
		ctx.text(font, head, x + 6, y + 7, p.accent)
		val vw = font.width("Vanilla") + 10
		m.active?.let { ctx.text(font, MenuTheme.fit("§aActive: §f${it.loadout.name}", w - font.width(head) - vw - 24, font::width), x + 12 + font.width(head), y + 7, MenuTheme.GREY) }

		if (LoadoutSettings.showCurrent) for (c in m.cells) {
			val r = lay.cell(c.col, c.row)
			val hover = r.contains(mx, my)
			MenuTheme.frame(ctx, r.x, r.y, r.x + r.w, r.y + r.h, if (c.empty) p.fillDisabled else if (hover) p.fillHover else p.fill, if (hover) p.borderHover else p.border)
			val st = c.slot?.let { stacks[it] }
			if (!c.empty && st != null) {
				ctx.item(st, r.x + (r.w - 16) / 2, r.y + 3); ctx.itemDecorations(font, st, r.x + (r.w - 16) / 2, r.y + 3)
				if (hover) tip = st
			}
			ctx.text(font, MenuTheme.fit(c.name, r.w - 4, font::width), r.x + 2, r.y + 21, if (c.empty) MenuTheme.DIM else MenuTheme.WHITE)
			c.summary?.let { if (r.h >= 38) ctx.text(font, "§7" + MenuTheme.fit(it, r.w - 4, font::width), r.x + 2, r.y + 30, MenuTheme.GREY) }
			if (c.empty && hover) textTip = listOf("§7${c.role.label}", "§8Nothing in this slot")
			if (!c.empty && c.slot != null) area(r.x, r.y, r.w, r.h) { b -> clickSlot(c.slot, b) }
		}

		val cards = m.cards.filter { LoadoutSettings.showLocked || !it.loadout.locked }
		ctx.text(font, "§eLoadouts §8(${cards.size})", lay.right.x, lay.right.y + 2, MenuTheme.WHITE)
		val ca = lay.cardsArea
		maxScroll = maxOf(0, lay.cardsHeight(cards.size) - ca.h); scroll = scroll.coerceIn(0, maxScroll)
		var hovered: LoadoutCard? = null
		ctx.enableScissor(ca.x, ca.y, ca.x + ca.w, ca.y + ca.h)
		cards.forEachIndexed { i, c ->
			val r = lay.card(i, scroll)
			if (r.y + r.h <= ca.y || r.y >= ca.y + ca.h) return@forEachIndexed
			val l = c.loadout
			val hover = r.contains(mx, my) && ca.contains(mx, my)
			if (hover) hovered = c
			MenuTheme.frame(ctx, r.x, r.y, r.x + r.w, r.y + r.h,
				if (l.locked) p.fillDisabled else if (l.active) p.fillActive else if (hover) p.fillHover else p.fill,
				if (l.active) p.accent else if (hover) p.borderHover else p.border)
			stacks[l.slot]?.let { ctx.item(it, r.x + 3, r.y + 3) }
			ctx.text(font, MenuTheme.fit(l.name, r.w - 24, font::width), r.x + 21, r.y + 4, if (l.locked) MenuTheme.DIM else MenuTheme.WHITE)
			val info = when {
				l.locked -> "§8" + (c.requirement.firstOrNull { it.contains("▶") }?.let(MenuText::clean) ?: "Locked")
				l.empty -> "§7Not set up"
				else -> "§7${c.setCount}/${c.total} set" + (l.info.firstOrNull()?.let { " · $it" } ?: "")
			}
			ctx.text(font, MenuTheme.fit(info, r.w - 24, font::width), r.x + 21, r.y + 15, MenuTheme.GREY)
			val cy0 = maxOf(r.y, ca.y); val cy1 = minOf(r.y + r.h, ca.y + ca.h)
			if (cy1 > cy0) area(r.x, cy0, r.w, cy1 - cy0) { b -> clickSlot(l.slot, b) }
		}
		ctx.disableScissor()
		hovered?.let { c -> stacks[c.loadout.slot]?.let { tip = it } }

		val d = lay.details
		MenuTheme.frame(ctx, d.x, d.y, d.x + d.w, d.y + d.h, p.fill, p.border)
		val sel = hovered ?: m.active
		if (sel != null) {
			val state = when { sel.loadout.locked -> "§cLocked"; sel.loadout.active -> "§aActive"; sel.loadout.empty -> "§7Not set up"; else -> "§fReady" }
			ctx.text(font, MenuTheme.fit("§f${sel.loadout.name} $state", d.w - 6, font::width), d.x + 3, d.y + 3, MenuTheme.WHITE)
			val line = (if (sel.loadout.locked) sel.requirement.firstOrNull() else sel.lore.firstOrNull { it.contains(": ") && !MenuText.clean(it).endsWith(": None") })
				?.let(MenuText::clean) ?: MenuText.clean(sel.lore.lastOrNull { it.isNotBlank() } ?: "")
			ctx.text(font, "§7" + MenuTheme.fit(line, d.w - 6, font::width), d.x + 3, d.y + 13, MenuTheme.GREY)
		} else ctx.text(font, "§7Hover a loadout for details", d.x + 3, d.y + 8, MenuTheme.GREY)

		var fx = lay.footer.x
		fun foot(label: String, slot: Int?) {
			if (slot == null) return
			val bw = minOf(font.width(label) + 12, 120)
			if (fx + bw > lay.footer.x + lay.footer.w) return
			button(ctx, mx, my, fx, lay.footer.y, bw, 14, label) { b -> clickSlot(slot, b) }
			fx += bw + 4
		}
		foot("< Prev", m.nav.prev)
		foot(m.nav.backLabel?.takeIf { it.startsWith("To ", true) }?.let { "< " + it.substring(3) } ?: "Back", m.nav.back)
		foot("Close", m.nav.close)
		for (b in m.extraButtons) foot(b.cleanName, b.slot)
		m.nav.next?.let { n -> val bw = font.width("Next >") + 12; button(ctx, mx, my, x + w - bw - 4, lay.footer.y, bw, 14, "Next >") { b -> clickSlot(n, b) } }

		if (screen.menu.carried.isEmpty) {
			tip?.let { ctx.setTooltipForNextFrame(font, it, mx, my) }
				?: textTip?.let { t -> ctx.setComponentTooltipForNextFrame(font, t.map { net.minecraft.network.chat.Component.literal(it) }, mx, my) }
		}
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		scroll = (scroll - (amount * 20).toInt()).coerceIn(0, maxScroll); return true
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		if (super.keyPressed(input)) return true
		val m = currentModel() ?: return false
		val c = LoadoutKeybinds.TConfig
		if (!c.enabled) return false
		val present = m.cards.map { it.loadout.slot }.toSet()
		val bindings = c.slotKeys.mapIndexed { i, k -> k.get() to LoadoutKeybinds.Logic.loadoutSlots.getOrNull(i)?.takeIf { it in present } } +
			listOf(c.previousPage to m.nav.prev, c.nextPage to m.nav.next)
		val hit = bindings.firstOrNull { TileKeys.matches(it.first, input) } ?: return false
		hit.second?.let { clickSlot(it, 0) }
		return true
	}
}
