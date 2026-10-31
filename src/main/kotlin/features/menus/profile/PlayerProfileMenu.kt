// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.profile

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.guides.GuideLore
import dev.ashtonite365.apex.features.menus.h6.HubModel
import dev.ashtonite365.apex.features.menus.h6.HubSection
import dev.ashtonite365.apex.features.menus.h6.HubText
import dev.ashtonite365.apex.features.menus.h6.HubTile
import dev.ashtonite365.apex.features.menus.h6.HubView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

object PlayerProfileParser {

	val TITLE = Regex("^(\\w{2,16})'s Profile$")

	private val EQUIPMENT_SLOTS = setOf(2, 10, 11, 19, 20, 28, 29, 37, 38, 47)
	private val EMPTY_PIECE = Regex("^Empty .* Slot$", RegexOption.IGNORE_CASE)

	val FIXED: Map<Int, Pair<String, String>> = linkedMapOf(
		11 to ("Helmet" to "helmet"), 20 to ("Chestplate" to "chestplate"), 29 to ("Leggings" to "leggings"), 38 to ("Boots" to "boots"),
		10 to ("Necklace" to "necklace"), 19 to ("Cloak" to "cloak"), 28 to ("Belt" to "belt"), 37 to ("Gloves / Bracelet" to "gloves"),
		2 to ("Held Item" to "held"), 47 to ("Pet" to "pet"),
	)
	const val EMPTY_PREFIX = "Empty: "
	fun isEmptyTile(t: HubTile) = t.slot in FIXED && t.name.startsWith(EMPTY_PREFIX)

	fun silhouette(slot: Int) = FIXED[slot]?.let { "apex:pv_empty_${it.second}" }
	private fun emptyTile(slot: Int) = (EMPTY_PREFIX + FIXED.getValue(slot).first).let { HubTile(slot, it, emptyList(), tooltip = emptyList(), clickable = false) }

	fun head(title: String, slots: List<SlotText>, chestSize: Int = 54): SlotText? {
		val who = name(title) ?: return null
		val navSlots = HubText.navOf(slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }).map { it.second }.toSet()
		return slots.firstOrNull { it.slot < chestSize && it.slot !in navSlots && it.cleanName.replace(Regex("^\\[[^]]+]\\s*"), "").equals(who, true) }
	}

	fun name(title: String) = TITLE.matchEntire(MenuText.clean(title).trim())?.groupValues?.get(1)

	fun parse(title: String, slots: List<SlotText>, chestSize: Int = 54): HubModel? {
		val who = name(title) ?: return null
		val chest = slots.filter { it.slot < chestSize && it.cleanName.isNotEmpty() }
		val nav = HubText.navOf(chest); val navSlots = nav.map { it.second }.toSet()

		val head = chest.firstOrNull { it.slot !in navSlots && it.cleanName.replace(Regex("^\\[[^]]+]\\s*"), "").equals(who, true) }
		val rest = chest.filter { it.slot != head?.slot && it.slot !in navSlots }
		if (head == null && rest.isEmpty()) return null
		val (gear, actions) = rest.partition { it.slot in EQUIPMENT_SLOTS }
		fun tile(s: SlotText, clickable: Boolean) = HubTile(s.slot, s.cleanName, listOf(GuideLore.info(s.lore)), tooltip = HubText.clean(s.lore), clickable = clickable)
		val chips = head?.let { h -> h.cleanLore.map { it.trim() }.filter { it.isNotEmpty() }.map { l ->
			val i = l.indexOf(':')
			if (i > 0) l.substring(0, i).trim() to l.substring(i + 1).trim()
			else Regex("^(.*?)\\s+([-+]?[\\d,.]+%?)$").matchEntire(l)?.let { it.groupValues[1] to it.groupValues[2] } ?: ("" to l)
		} }.orEmpty()
		return HubModel(head?.cleanName ?: "$who's Profile", chips, listOf(
			HubSection("Equipment", FIXED.keys.sorted().map { slot ->
				gear.firstOrNull { it.slot == slot && !EMPTY_PIECE.matches(it.cleanName) }?.let { tile(it, false) } ?: emptyTile(slot)
			}),
			HubSection("Actions", actions.map { tile(it, true) }),
		).filter { it.tiles.isNotEmpty() }, nav)
	}
}

@Config object PlayerProfileMenuSettings : MenuSettings("player-profile") { val reset by button("reset") { resetToDefaults() } }

object PlayerProfileMenu : CustomMenu {
	override val id = "player-profile"
	override val displayName = "Player Profile"
	override val title = PlayerProfileParser.TITLE
	override val settings get() = PlayerProfileMenuSettings
	override val defaultSize = 460 to 280
	@Subscribe fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView? = if (screen.menu.slots.size > 36) PlayerProfileView(screen) else null
}

object PlayerProfileLayout {
	const val COLS = 9
	const val GAP = 2
	data class Cell(val x: Int, val y: Int, val w: Int, val h: Int)

	fun cellSize(w: Int, h: Int, rows: Int): Pair<Int, Int> {
		val cw = ((w - (COLS - 1) * GAP) / COLS).coerceAtLeast(18)
		val ch = ((h - (rows - 1) * GAP) / rows.coerceAtLeast(1)).coerceIn(18, (cw * 1.4).toInt().coerceAtLeast(18))
		return cw to ch
	}

	fun cell(slot: Int, ox: Int, oy: Int, cw: Int, ch: Int, firstRow: Int = 0) =
		Cell(ox + (slot % COLS) * (cw + GAP), oy + (slot / COLS - firstRow) * (ch + GAP), cw, ch)

	fun rowSpan(slots: Collection<Int>): IntRange = if (slots.isEmpty()) 0..0 else (slots.min() / COLS)..(slots.max() / COLS)
}

data class PlayerProfileModel(val hub: HubModel, val head: SlotText?)

class PlayerProfileView(screen: AbstractContainerScreen<*>) : dev.ashtonite365.apex.features.menus.support.ApexViewBase<PlayerProfileModel>(screen) {
	private companion object { const val EMPTY_TINT = 0x59FFFFFF }
	override fun palette() = PlayerProfileMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>): PlayerProfileModel? {
		val size = screen.menu.slots.size - 36
		val hub = PlayerProfileParser.parse(title, slots, size) ?: return null
		return PlayerProfileModel(hub, PlayerProfileParser.head(title, slots, size))
	}

	override fun draw(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, pm: PlayerProfileModel, mx: Int, my: Int) {
		val m = pm.hub
		val p = palette(); val pad = 6
		ctx.text(font, dev.ashtonite365.apex.features.menus.MenuTheme.fit("§6§l" + m.heading, w / 2, font::width), x + pad, y + 7, dev.ashtonite365.apex.features.menus.MenuTheme.WHITE)
		var bx = x + w - 4
		val extra = PlayerProfileParser.name(screen.title.string)?.takeIf { dev.ashtonite365.apex.features.profileviewer.ProfileViewer.TConfig.profileMenuButton }
			?.let { n -> listOf<Pair<String, () -> Unit>>("Open PV" to { dev.ashtonite365.apex.features.profileviewer.ProfileViewer.open(n) }) }.orEmpty()
		for ((label, act) in emptyList<Pair<String, () -> Unit>>() + extra) {
			val bw = font.width(label) + 10; bx -= bw
			button(ctx, mx, my, bx, y + 3, bw, 14, label) { act() }; bx -= 4
		}
		var top = y + 22

		if (m.chips.isNotEmpty()) {
			val inner = w - 2 * pad
			val rows = dev.ashtonite365.apex.features.menus.h6.HubLayout.chipRows(m.chips, inner, font::width)
			val ph = dev.ashtonite365.apex.features.menus.h6.HubLayout.chipPanelHeight(rows.size)
			dev.ashtonite365.apex.features.menus.MenuTheme.panel(ctx, x + pad, top, inner, ph, p)
			rows.forEachIndexed { r, idx ->
				val row = idx.map { m.chips[it] }
				var cx = x + pad
				dev.ashtonite365.apex.features.menus.h6.HubLayout.chipColumns(row, inner, font::width).forEachIndexed { i, cw ->
					val (l, v) = row[i]
					val ry = top + 4 + r * dev.ashtonite365.apex.features.menus.h6.HubLayout.CHIP_ROW_H
					ctx.text(font, dev.ashtonite365.apex.features.menus.MenuTheme.fit("§7$l", cw - 8, font::width), cx + 5, ry, dev.ashtonite365.apex.features.menus.MenuTheme.GREY)
					ctx.text(font, dev.ashtonite365.apex.features.menus.MenuTheme.fit("§f$v", cw - 8, font::width), cx + 5, ry + 10, dev.ashtonite365.apex.features.menus.MenuTheme.WHITE)
					cx += cw
				}
			}
			top += ph + 4
		}

		val tiles = m.allTiles
		val headTile = pm.head?.let { HubTile(it.slot, it.cleanName, emptyList(), tooltip = HubText.clean(it.lore), clickable = false) }
		val all = tiles + listOfNotNull(headTile)
		val span = PlayerProfileLayout.rowSpan(all.map { it.slot })
		val navTop = dev.ashtonite365.apex.features.menus.h6.HubLayout.navTop(y, h)
		val (cw, ch) = PlayerProfileLayout.cellSize(w - 2 * pad, navTop - 4 - top, span.last - span.first + 1)
		val gridW = PlayerProfileLayout.COLS * cw + (PlayerProfileLayout.COLS - 1) * PlayerProfileLayout.GAP
		val ox = x + (w - gridW) / 2
		var hover: HubTile? = null
		val ts = if (cw >= 60) 1f else 0.75f
		for (t in all) {
			val c = PlayerProfileLayout.cell(t.slot, ox, top, cw, ch, span.first)
			val hov = hovered(mx, my, c.x, c.y, c.w, c.h)
			dev.ashtonite365.apex.features.menus.MenuTheme.button(ctx, font, c.x, c.y, c.w, c.h, "", dev.ashtonite365.apex.features.menus.MenuTheme.state(hov, false, true), palette = p)
			val empty = PlayerProfileParser.isEmptyTile(t)
			if (empty) PlayerProfileParser.silhouette(t.slot)?.let {
				ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.resources.Identifier.parse(it),
					c.x + (c.w - 16) / 2, c.y + 2, 16, 16, EMPTY_TINT)
			} else stacks[t.slot]?.let { ctx.item(it, c.x + (c.w - 16) / 2, c.y + 2) }

			if (c.h >= 30 && !empty) {
				val lines = dev.ashtonite365.apex.features.menus.support.EnchantLabel.wrap(t.name, ((c.w - 4) / ts).toInt()) { font.width(it) }
				val maxLines = ((c.h - 20) / (9 * ts)).toInt().coerceAtLeast(0)
				if (lines.size <= maxLines) dev.ashtonite365.apex.features.menus.garden.GardenFit.drawLines(ctx, font,
					lines.map { "§f$it" }, c.x + 2, c.y + 19, ts, dev.ashtonite365.apex.features.menus.MenuTheme.WHITE)
			}
			if (t.clickable && !t.locked) area(c.x, c.y, c.w, c.h) { b -> clickSlot(t.slot, if (b == 1) 1 else t.button) }
			if (hov) hover = t
		}

		var nx = x + pad
		for ((label, slot) in m.nav) {
			val bw = font.width(label) + 10
			button(ctx, mx, my, nx, navTop, bw, 14, label) { clickSlot(slot, 0) }; nx += bw + 4
		}
		hover?.let { t ->
			val lines = (listOf(t.name) + (t.tooltip.ifEmpty { t.info })).map { net.minecraft.network.chat.Component.literal(it) }
			ctx.setComponentTooltipForNextFrame(font, lines, mx, my)
		}
	}
}
