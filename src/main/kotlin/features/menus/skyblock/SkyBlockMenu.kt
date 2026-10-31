// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skyblock

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
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

enum class SbGroup(val label: String) { PROFILE("You"), PROGRESS("Progress"), UTILITY("Utilities"), NAVIGATION("Navigation") }

enum class SbTile(val label: String, val group: SbGroup, val dumpSlot: Int, val info: String? = null) {
	STATS("Stats & Equipment", SbGroup.PROFILE, 13, "Health"),
	SKILLS("Your Skills", SbGroup.PROGRESS, 19, "Skill Avg"),
	COLLECTIONS("Collections", SbGroup.PROGRESS, 20, "Collections Unlocked"),
	RECIPES("Recipe Book", SbGroup.PROGRESS, 21, "Recipes Unlocked"),
	LEVELING("SkyBlock Leveling", SbGroup.PROGRESS, 22, "Your SkyBlock Level"),
	QUESTS("Quests & Chapters", SbGroup.PROGRESS, 23),
	CALENDAR("Calendar and Events", SbGroup.UTILITY, 24, "Date"),
	STORAGE("Storage", SbGroup.UTILITY, 25),
	BAGS("Your Bags", SbGroup.UTILITY, 29),
	PETS("Pets", SbGroup.UTILITY, 30, "Selected pet"),
	CRAFTING("Crafting Table", SbGroup.UTILITY, 31),
	LOADOUTS("Loadouts", SbGroup.UTILITY, 32),
	BANK("Personal Bank", SbGroup.UTILITY, 33, "Interest in"),
	FAST_TRAVEL("Fast Travel", SbGroup.NAVIGATION, 47),
	PROFILES("Profile Management", SbGroup.NAVIGATION, 48, "Profiles"),
	SETTINGS("Settings", SbGroup.NAVIGATION, 50),
	COOKIE("Booster Cookie", SbGroup.PROFILE, 51, "Duration"),
	CLOSE("Close", SbGroup.NAVIGATION, 49),
}

data class SbMenuModel(val tiles: Map<SbTile, Pair<Int, String?>>, val chips: SlotText? = null)

object SkyBlockMenuParser {
	val TITLE = Regex("^(?i)SkyBlock Menu$")
	const val CHEST_SIZE = 54

	val REQUIRED = listOf(SbTile.STATS, SbTile.FAST_TRAVEL, SbTile.PROFILES, SbTile.SETTINGS)

	val CHIPS = Regex("(?i)\\bchips?\\b")

	fun chips(chest: List<SlotText>): SlotText? = chest.firstOrNull { s ->
		CHIPS.containsMatchIn(s.cleanName) && SbTile.entries.none { it.label.equals(s.cleanName, true) }
	}

	fun info(lore: List<String>, prefix: String): String? {
		val l = lore.firstOrNull { it.trimStart().startsWith(prefix, true) } ?: return null
		val rest = l.trimStart().substring(prefix.length).trimStart(':', ' ').trim()
		return rest.ifEmpty { null }
	}

	private fun infoFor(t: SbTile, s: SlotText): String? {
		val lore = s.cleanLore
		return when (t) {

			SbTile.SKILLS -> lore.firstOrNull { "Skill Avg" in it }?.substringBefore(" Skill")?.let { "$it avg" }
			SbTile.PROFILES -> lore.firstOrNull { it.startsWith("Profiles:") }?.substringAfter(":")?.trim()
			else -> t.info?.let { info(lore, it) }
		}
	}

	fun parse(title: String, slots: List<SlotText>): SbMenuModel? {
		if (!TITLE.matches(MenuText.clean(title))) return null
		val chest = slots.filter { it.slot < CHEST_SIZE }
		val tiles = linkedMapOf<SbTile, Pair<Int, String?>>()
		for (t in SbTile.entries) chest.firstOrNull { it.cleanName.equals(t.label, true) }?.let { tiles[t] = it.slot to infoFor(t, it) }
		if (REQUIRED.any { it !in tiles }) return null
		return SbMenuModel(tiles, chips(chest))
	}
}

@Config
object SkyBlockMenuSettings : MenuSettings("skyblock-menu", panels = listOf("live-info")) {
	val reset by button("reset") { resetToDefaults() }
}

object SkyBlockMenu : CustomMenu {
	override val id = "skyblock-menu"
	override val displayName = "SkyBlock Menu"
	override val title = SkyBlockMenuParser.TITLE
	override val settings get() = SkyBlockMenuSettings

	override val defaultSize = 420 to 256

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = SkyBlockMenuView(screen)
}

object SkyBlockMenuLayout {
	const val PAD = 4
	const val HEADER = 17
	const val GAP = 2

	const val COLS = 4

	const val NAV_H = 42
	const val MAX_ROW_H = 48

	const val ICON_MIN_H = 20

	const val TEXT_X = 20

	data class Cell(val tile: SbTile, val slot: Int, val x: Int, val y: Int, val w: Int, val h: Int) {
		fun contains(mx: Int, my: Int) = mx >= x && mx < x + w && my >= y && my < y + h
		val bounds get() = MenuBounds(x, y, w, h)
		val showsIcon get() = h >= ICON_MIN_H && w >= 40
	}

	private fun row(tiles: List<Pair<SbTile, Int>>, n: Int, b: MenuBounds, y: Int, h: Int, wide: Boolean = false): List<Cell> {
		val availW = b.w - 2 * PAD
		val cw = maxOf(1, (availW - (n - 1) * GAP) / n)
		val tw = if (wide) minOf(availW, 2 * cw + GAP) else cw
		val used = tiles.size * tw + (tiles.size - 1) * GAP
		val x0 = b.x + PAD + (availW - used) / 2
		return tiles.mapIndexed { i, (t, slot) -> Cell(t, slot, x0 + i * (tw + GAP), y, tw, h) }
	}

	fun cells(m: SbMenuModel, b: MenuBounds): List<Cell> {
		val all = m.tiles.map { (t, v) -> t to v.first }.sortedBy { it.second }
		if (all.isEmpty()) return emptyList()
		val rows = all.map { it.second / 9 }
		val topRow = rows.min(); val bottomRow = rows.max()
		val top = if (bottomRow > topRow) all.filter { it.second / 9 == topRow } else emptyList()
		val bottom = all.filter { it.second / 9 == bottomRow }
		val middle = all - top.toSet() - bottom.toSet()
		val out = mutableListOf<Cell>()
		val navH = minOf(NAV_H, maxOf(1, (b.h - HEADER - PAD) / 3))
		val navY = b.y + b.h - PAD - navH
		out += row(bottom, maxOf(bottom.size, 1), b, navY, navH)
		val midRows = middle.chunked(COLS)
		val bodyRows = (if (top.isNotEmpty()) 1 else 0) + midRows.size
		if (bodyRows == 0) return out
		val bodyTop = b.y + HEADER; val bodyBottom = navY - 2 * GAP
		val rowH = maxOf(1, minOf(MAX_ROW_H, (bodyBottom - bodyTop - (bodyRows - 1) * GAP) / bodyRows))
		val usedH = bodyRows * rowH + (bodyRows - 1) * GAP
		var y = bodyTop + maxOf(0, (bodyBottom - bodyTop - usedH) / 2)
		if (top.isNotEmpty()) { out += row(top, COLS, b, y, rowH, wide = true); y += rowH + GAP }
		for (r in midRows) { out += row(r, COLS, b, y, rowH); y += rowH + GAP }
		return out
	}

	fun vanillaButton(b: MenuBounds, labelW: Int): MenuBounds {
		val bw = labelW + 10
		return MenuBounds(b.x + b.w - bw - 3, b.y + 2, bw, 13)
	}

	fun deskButton(cells: List<Cell>, b: MenuBounds, labelW: Int): Pair<MenuBounds, List<Cell>>? {
		val stats = cells.firstOrNull { it.tile == SbTile.STATS } ?: return null
		val bw = labelW + 10; val bh = minOf(13, stats.h)
		val left = b.x + PAD
		val need = left + bw + GAP
		val adj = if (stats.x >= need) stats else stats.copy(x = need, w = maxOf(1, stats.x + stats.w - need))
		val desk = MenuBounds(adj.x - GAP - bw, stats.y + (stats.h - bh) / 2, bw, bh)
		return desk to cells.map { if (it === stats) adj else it }
	}

	fun deskEnabled(onGarden: Boolean) = onGarden

	fun deskClick(enabled: Boolean, later: (() -> Unit) -> Unit, sendCommand: (String) -> Unit): Boolean {
		if (!enabled) return false
		later { sendCommand("desk") }
		return true
	}

	fun grid(b: MenuBounds): Pair<Int, Int> {
		val availW = b.w - 2 * PAD
		val cw = maxOf(1, (availW - (COLS - 1) * GAP) / COLS)
		return (b.x + PAD + (availW - (COLS * cw + (COLS - 1) * GAP)) / 2) to cw
	}

	fun wardrobeTile(cells: List<Cell>, b: MenuBounds, labelW: Int = WARDROBE_LABEL_W): Pair<MenuBounds, List<Cell>>? {
		val stats = cells.firstOrNull { it.tile == SbTile.STATS } ?: return null
		val (x0, cw) = grid(b)
		val right = x0 + COLS * cw + (COLS - 1) * GAP
		val icon = stats.h >= ICON_MIN_H
		val w = minOf(cw, maxOf(24, (if (icon) TEXT_X else 4) + labelW + 6))
		val x = right - w
		val statsW = x - GAP - stats.x
		if (statsW < 16 || w < 16) return null
		return MenuBounds(x, stats.y, w, stats.h) to cells.map { if (it === stats) it.copy(w = statsW) else it }
	}

	const val WARDROBE_LABEL_W = 48

	const val APEX_MIN = 14

	fun apexButton(cells: List<Cell>, b: MenuBounds, leftOf: Int? = null): MenuBounds? {
		val stats = cells.firstOrNull { it.tile == SbTile.STATS } ?: return null
		val (x0, _) = grid(b)
		val s = minOf(stats.h, (leftOf ?: stats.x) - GAP - x0)
		if (s < APEX_MIN) return null
		return MenuBounds(x0, stats.y + (stats.h - s) / 2, s, s)
	}

	fun apexIcon(a: MenuBounds): MenuBounds = MenuBounds(a.x + 3, a.y + 3, a.w - 6, a.h - 6)

	fun apexClick(later: (() -> Unit) -> Unit, open: () -> Unit) { later(open) }

	const val WARDROBE_ICON_ID = "minecraft:leather_chestplate"
	const val WARDROBE_LABEL = "Wardrobe"

	fun wardrobeClick(later: (() -> Unit) -> Unit, sendCommand: (String) -> Unit) { later { sendCommand("wardrobe") } }

	const val CHIPS_SIZE = 18

	fun chipsButton(desk: MenuBounds, b: MenuBounds): MenuBounds? {
		val x = desk.x - GAP - CHIPS_SIZE
		if (x < b.x + PAD) return null
		return MenuBounds(x, desk.y + desk.h / 2 - CHIPS_SIZE / 2, CHIPS_SIZE, CHIPS_SIZE)
	}

	fun chipsClick(m: SbMenuModel, button: Int, clickSlot: (Int, Int) -> Unit): Boolean {
		val s = m.chips ?: return false
		clickSlot(s.slot, button)
		return true
	}

	fun tooltip(t: SbTile, info: String?): List<String> = listOfNotNull("§f" + t.label, info?.let { "§7$it" })

	data class Fitted(val scale: Float, val lines: List<String>) {
		fun height(lineH: Int = MenuTheme.TEXT_H) = lines.size * lineH * scale
	}

	const val MIN_SCALE = 0.5f

	fun fitText(text: String, w: Int, h: Int, measure: (String) -> Int, lineH: Int = MenuTheme.TEXT_H): Fitted {
		if (w <= 0 || h <= 0 || text.isEmpty()) return Fitted(1f, emptyList())
		for (k in 20 downTo Math.round(MIN_SCALE * 20)) {
			val sc = k / 20f
			val ww = (w / sc).toInt()
			val lines = wrap(text, ww, measure)
			if (lines.all { measure(it) <= ww } && lines.size * lineH * sc <= h + 1e-3f) return Fitted(sc, lines)
		}
		val s = MIN_SCALE
		val ww = (w / s).toInt()
		val max = maxOf(1, (h / (lineH * s)).toInt())
		val lines = wrap(text, ww, measure)
		val kept = lines.take(max).toMutableList()
		if (lines.size > max) kept[kept.size - 1] = MenuTheme.fit(kept.last() + " " + lines[max], ww, measure).let { if (it.endsWith("…")) it else "$it…" }
		return Fitted(s, kept.map { MenuTheme.fit(it, ww, measure) })
	}

	fun wrap(text: String, w: Int, measure: (String) -> Int): List<String> {
		val out = mutableListOf<String>(); var cur = ""
		for (word in text.split(' ').filter { it.isNotEmpty() }) {
			val next = if (cur.isEmpty()) word else "$cur $word"
			if (cur.isNotEmpty() && measure(next) > w) { out += cur; cur = word } else cur = next
		}
		if (cur.isNotEmpty()) out += cur
		return out
	}

	fun textBox(c: Cell): MenuBounds {
		val left = if (c.showsIcon) TEXT_X else 2
		return MenuBounds(c.x + left, c.y + 2, maxOf(1, c.w - left - 2), maxOf(1, c.h - 4))
	}

	data class TileText(val box: MenuBounds, val scale: Float, val lines: List<String>, val labelLines: Int) {
		fun height(lineH: Int = MenuTheme.TEXT_H) = lines.size * lineH * scale
	}

	fun tileText(c: Cell, label: String, info: String?, measure: (String) -> Int, lineH: Int = MenuTheme.TEXT_H): TileText {
		val box = textBox(c)
		for (k in 20 downTo Math.round(MIN_SCALE * 20)) {
			val sc = k / 20f
			val ww = (box.w / sc).toInt()
			val l = wrap(label, ww, measure); val i = info?.let { wrap(it, ww, measure) } ?: emptyList()
			if ((l + i).all { measure(it) <= ww } && (l.size + i.size) * lineH * sc <= box.h + 1e-3f) return TileText(box, sc, l + i, l.size)
		}

		val f = fitText(label, box.w, box.h, measure, lineH)
		return TileText(box, f.scale, f.lines, f.lines.size)
	}
}

class SkyBlockMenuView(screen: AbstractContainerScreen<*>) : ApexViewBase<SbMenuModel>(screen) {
	override fun palette() = SkyBlockMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = SkyBlockMenuParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: SbMenuModel, mx: Int, my: Int) {
		val b = MenuBounds(x, y, w, h)
		val vb = SkyBlockMenuLayout.vanillaButton(b, font.width("Vanilla"))
		ctx.text(font, "§6§lSkyBlock Menu", x + SkyBlockMenuLayout.PAD + 2, y + 5, MenuTheme.GOLD)

		val live = SkyBlockMenuSettings.shows("live-info")
		var hover: SkyBlockMenuLayout.Cell? = null
		var cells = SkyBlockMenuLayout.cells(m, b)
		var deskTip: List<Component>? = null
		var chipsTip: List<Component>? = null
		SkyBlockMenuLayout.deskButton(cells, b, font.width(DESK_LABEL.string))?.let { (db, adjusted) ->
			m.chips?.let { ch -> SkyBlockMenuLayout.chipsButton(db, b)?.let { cb ->
				button(ctx, mx, my, cb.x, cb.y, cb.w, cb.h, "") { btn -> SkyBlockMenuLayout.chipsClick(m, btn, ::clickSlot) }
				val st = stacks[ch.slot]
				st?.let { ctx.item(it, cb.x + (cb.w - 16) / 2, cb.y + (cb.h - 16) / 2) }
				if (st != null && mx >= cb.x && mx < cb.x + cb.w && my >= cb.y && my < cb.y + cb.h)
					chipsTip = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(dev.ashtonite365.apex.util.Client.game, st)
			} }
			cells = adjusted
			val on = SkyBlockMenuLayout.deskEnabled(dev.ashtonite365.apex.util.SkyBlockSession.skyblockIsland == dev.ashtonite365.apex.util.SkyBlockIsland.GARDEN)
			val hov = mx >= db.x && mx < db.x + db.w && my >= db.y && my < db.y + db.h
			MenuTheme.button(ctx, font, db.x, db.y, db.w, db.h, DESK_LABEL.string, MenuTheme.state(hov, false, on), palette = palette())
			if (on) area(db.x, db.y, db.w, db.h) { _ ->
				SkyBlockMenuLayout.deskClick(true, dev.ashtonite365.apex.util.ApexClickDelay::later, dev.ashtonite365.apex.util.Client::runCommand)
			}
			if (hov) deskTip = listOf(if (on) DESK_TOOLTIP else DESK_GARDEN_ONLY)
		}
		var wardrobeTip = false
		var apexLeftOf: Int? = null
		SkyBlockMenuLayout.deskButton(cells, b, font.width(DESK_LABEL.string))?.let { (db, _) ->
			apexLeftOf = m.chips?.let { SkyBlockMenuLayout.chipsButton(db, b)?.x } ?: db.x
		}
		var apexTip = false
		SkyBlockMenuLayout.apexButton(cells, b, apexLeftOf)?.let { ab ->
			button(ctx, mx, my, ab.x, ab.y, ab.w, ab.h, "") { _ ->
				SkyBlockMenuLayout.apexClick(dev.ashtonite365.apex.util.ApexClickDelay::later) { dev.ashtonite365.apex.gui.config.AllConfigsGui.showAllGuis() }
			}
			val r = SkyBlockMenuLayout.apexIcon(ab)
			val hov = mx >= ab.x && mx < ab.x + ab.w && my >= ab.y && my < ab.y + ab.h
			ctx.blit(dev.ashtonite365.apex.features.menus.ApexInventoryButton.sprite(hov), r.x, r.y, r.x + r.w, r.y + r.h, 0f, 1f, 0f, 1f)
			if (hov) apexTip = true
		}
		SkyBlockMenuLayout.wardrobeTile(cells, b, font.width(WARDROBE_LABEL.string))?.let { (wb, adjusted) ->
			cells = adjusted
			button(ctx, mx, my, wb.x, wb.y, wb.w, wb.h, "") { _ ->
				SkyBlockMenuLayout.wardrobeClick(dev.ashtonite365.apex.util.ApexClickDelay::later, dev.ashtonite365.apex.util.Client::runCommand)
			}
			val wc = SkyBlockMenuLayout.Cell(SbTile.STATS, -1, wb.x, wb.y, wb.w, wb.h)
			if (wc.showsIcon) ctx.item(WARDROBE_ICON, wb.x + 2, wb.y + (wb.h - 16) / 2)
			val t = SkyBlockMenuLayout.tileText(wc, WARDROBE_LABEL.string, null, font::width)
			var ty = t.box.y + ((t.box.h - t.height()) / 2).coerceAtLeast(0f)
			for (l in t.lines) {
				ctx.pose().pushMatrix(); ctx.pose().translate(t.box.x.toFloat(), ty); ctx.pose().scale(t.scale, t.scale)
				ctx.text(font, l, 0, 0, MenuTheme.WHITE, true)
				ctx.pose().popMatrix()
				ty += MenuTheme.TEXT_H * t.scale
			}
			if (wc.contains(mx, my)) wardrobeTip = true
		}
		for (c in cells) {
			button(ctx, mx, my, c.x, c.y, c.w, c.h, "") { btn -> clickSlot(c.slot, btn) }
			if (c.showsIcon) stacks[c.slot]?.let { ctx.item(it, c.x + 2, c.y + (c.h - 16) / 2) }
			val info = m.tiles[c.tile]?.second?.takeIf { live }
			val t = SkyBlockMenuLayout.tileText(c, c.tile.label, info, font::width)
			var ty = t.box.y + ((t.box.h - t.height()) / 2).coerceAtLeast(0f)
			t.lines.forEachIndexed { i, l ->
				ctx.pose().pushMatrix(); ctx.pose().translate(t.box.x.toFloat(), ty); ctx.pose().scale(t.scale, t.scale)
				ctx.text(font, l, 0, 0, if (i < t.labelLines) MenuTheme.WHITE else MenuTheme.GREY, true)
				ctx.pose().popMatrix()
				ty += MenuTheme.TEXT_H * t.scale
			}
			if (c.contains(mx, my)) hover = c
		}
		hover?.let { c ->
			val lines = SkyBlockMenuLayout.tooltip(c.tile, m.tiles[c.tile]?.second).map { Component.literal(it) }
			ctx.setComponentTooltipForNextFrame(font, lines, mx, my)
		}
		deskTip?.let { ctx.setComponentTooltipForNextFrame(font, it, mx, my) }
		chipsTip?.let { ctx.setComponentTooltipForNextFrame(font, it, mx, my) }
		if (wardrobeTip) ctx.setComponentTooltipForNextFrame(font, listOf(WARDROBE_LABEL, WARDROBE_TOOLTIP), mx, my)
		if (apexTip) ctx.setComponentTooltipForNextFrame(font, listOf(Component.literal("SkyBlock Apex")), mx, my)
	}

	companion object {
		val DESK_LABEL: Component = Component.translatableWithFallback("apex.menus.skyblock.desk", "Desk")
		val DESK_TOOLTIP: Component = Component.translatableWithFallback("apex.menus.skyblock.desk.tooltip", "Open the Garden Desk")
		val DESK_GARDEN_ONLY: Component = Component.translatableWithFallback("apex.menus.skyblock.desk.garden-only", "§7Only works on the Garden")
		val WARDROBE_LABEL: Component = Component.translatableWithFallback("apex.menus.skyblock.wardrobe", "Wardrobe")
		val WARDROBE_TOOLTIP: Component = Component.translatableWithFallback("apex.menus.skyblock.wardrobe.tooltip", "§7Open your Wardrobe")

		val WARDROBE_ICON by lazy { net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE) }
	}
}
