// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.wardrobe

import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.KeyEvent
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.inventory.WardrobeKeybinds
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTitles
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.PageNav
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.Tile
import dev.ashtonite365.apex.features.menus.support.TileGroup
import dev.ashtonite365.apex.features.menus.support.TileKeys
import dev.ashtonite365.apex.features.menus.support.TileParse
import dev.ashtonite365.apex.features.menus.support.TileViewBase
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.features.menus.MenuTheme
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

enum class SetState { EQUIPPED, READY, EMPTY, LOCKED }

data class WardrobeSet(val index: Int, val buttonSlot: Int, val number: Int, val state: SetState, val pieces: List<String>)

data class WardrobeModel(val sets: List<WardrobeSet>, val nav: PageNav, val slots: List<SlotText> = emptyList())

object WardrobeParser {

	val KEYBIND_TITLE = Regex("\\([0-9]+/[0-9]+\\) (?:Armor|Equipment) Sets")
	val TITLE = MenuTitles.anyOf(
		Regex("^${KEYBIND_TITLE.pattern}$"),
		MenuTitles.endsWith("Armor Sets", prefix = "^\\(\\d+/\\d+\\) "),
		MenuTitles.endsWith("Equipment Sets", prefix = "^\\(\\d+/\\d+\\) "),
		Regex("^Wardrobe(?: \\(\\d+/\\d+\\))?$"))

	internal val PLACEHOLDER = Regex("^Slot \\d+\\b.*", RegexOption.IGNORE_CASE)
	const val BUTTON_ROW = 36
	const val PREV = 45
	const val NEXT = 53
	private val BUTTON = Regex("^Slot (\\d+)(?::\\s*(.+))?$", RegexOption.IGNORE_CASE)

	fun state(s: SlotText, suffix: String?): SetState {
		when (suffix?.trim()?.lowercase()) {
			"equipped" -> return SetState.EQUIPPED
			"ready" -> return SetState.READY
			"empty" -> return SetState.EMPTY
			"locked" -> return SetState.LOCKED
		}
		val t = (suffix ?: "") + " " + s.cleanLore.joinToString(" ")
		return when {
			t.contains("Equipped", true) && !t.contains("Click to equip", true) -> SetState.EQUIPPED
			t.contains("Locked", true) || t.contains("Requires", true) -> SetState.LOCKED
			t.contains("Empty", true) -> SetState.EMPTY
			else -> SetState.READY
		}
	}

	fun parse(title: String, slots: List<SlotText>): WardrobeModel? {
		val t = MenuText.clean(title)
		if (!TITLE.containsMatchIn(t)) return null
		val by = slots.associateBy { it.slot }
		val sets = (0..8).mapNotNull { i ->
			val b = by[BUTTON_ROW + i] ?: return@mapNotNull null
			val m = BUTTON.matchEntire(b.cleanName) ?: return@mapNotNull null
			val pieces = armour(by, i).mapNotNull { it?.cleanName }
			WardrobeSet(i, BUTTON_ROW + i, m.groupValues[1].toIntOrNull() ?: (i + 1), state(b, m.groupValues[2].takeIf(String::isNotEmpty)), pieces)
		}
		if (sets.isEmpty()) return null
		val page = TileParse.page(t)
		val nav = TileParse.nav(slots, 54, page).let { n ->
			n.copy(prev = n.prev ?: by[PREV]?.takeIf { it.cleanName.contains("Previous", true) }?.slot,
				next = n.next ?: by[NEXT]?.takeIf { it.cleanName.contains("Next", true) }?.slot)
		}
		return WardrobeModel(sets, nav, slots)
	}

	enum class CellKind { PIECE, SET_BUTTON }
	data class Cell(val slot: Int, val row: Int, val col: Int, val kind: CellKind)

	fun cells(slots: List<SlotText>): List<Cell> {
		val by = slots.associateBy { it.slot }
		return (0 until BUTTON_ROW).map { Cell(it, it / 9, it % 9, CellKind.PIECE) } +
			(BUTTON_ROW until BUTTON_ROW + 9).filter { by[it]?.let { s -> !TileParse.isFiller(s) } == true }
				.map { Cell(it, 4, it - BUTTON_ROW, CellKind.SET_BUTTON) }
	}

	fun armour(by: Map<Int, SlotText>, i: Int): List<SlotText?> =
		(0..3).map { r -> by[r * 9 + i]?.takeUnless { TileParse.isFiller(it) || PLACEHOLDER.matches(it.cleanName) } }

	fun page(title: String) = TileParse.page(MenuText.clean(title))

	fun keySlot(m: WardrobeModel, k: Int, allowUnequip: Boolean): Int? {
		val s = m.sets.firstOrNull { it.index == k } ?: return null
		return when (s.state) {
			SetState.READY -> s.buttonSlot
			SetState.EQUIPPED -> if (allowUnequip) s.buttonSlot else null
			else -> null
		}
	}
}

object WardrobeScale {
	const val FACTOR = 1.2f

	const val MARGIN = 12
	const val MIN = 0.25f

	data class Fit(val scale: Float, val x: Int, val y: Int, val w: Int, val h: Int, val logicalW: Int, val logicalH: Int) {
		fun toScreenX(lx: Int) = x + Math.round((lx - x) * scale)
		fun toScreenY(ly: Int) = y + Math.round((ly - y) * scale)
		fun toLocalX(mx: Double) = x + (mx - x) / scale
		fun toLocalY(my: Double) = y + (my - y) / scale
	}

	fun scale(baseW: Int, baseH: Int, screenW: Int, screenH: Int, target: Float = FACTOR, margin: Int = MARGIN): Float {
		if (baseW <= 0 || baseH <= 0) return 1f
		val fits = minOf((screenW - margin).toFloat() / baseW, (screenH - margin).toFloat() / baseH)
		return when {
			fits >= target -> target
			fits >= 1f -> fits
			else -> fits.coerceAtLeast(MIN)
		}
	}

	fun fit(baseW: Int, baseH: Int, screenW: Int, screenH: Int, target: Float = FACTOR, margin: Int = MARGIN): Fit {
		val s = scale(baseW, baseH, screenW, screenH, target, margin)
		val w = (baseW * s).toInt(); val h = (baseH * s).toInt()
		return Fit(s, (screenW - w) / 2, (screenH - h) / 2, w, h, baseW, baseH)
	}
}

@Config
object WardrobeSettings : MenuSettings("wardrobe") {
	val showPieces by toggle("show-pieces") { true }
	val reset by button("reset") { resetToDefaults() }
}

object WardrobeMenu : CustomMenu {
	override val id = "wardrobe"
	override val displayName = "Wardrobe"
	override val title = WardrobeParser.TITLE
	override val settings get() = WardrobeSettings
	override val defaultSize = 460 to 220 + InventoryPanel.RESERVED

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }
	override fun create(screen: AbstractContainerScreen<*>): MenuView = WardrobeView(screen)
}

class WardrobeView(screen: AbstractContainerScreen<*>) : TileViewBase<WardrobeModel>(screen) {
	override fun palette() = WardrobeSettings.customisation.palette

	private var screenMouseX = 0; private var screenMouseY = 0

	private var subtitleExtraLines = 0

	override fun layout(x: Int, y: Int, w: Int, h: Int) {
		val sw = screen.width.takeIf { it > 0 } ?: (2 * x + w)
		val sh = screen.height.takeIf { it > 0 } ?: (2 * y + h)
		val f = dev.ashtonite365.apex.features.menus.style.WardrobeLayout.frame(sw, sh)
		super.layout(f.x, f.y, f.w, f.h)
	}

	override fun render(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		screenMouseX = mouseX; screenMouseY = mouseY
		super.render(ctx, mouseX, mouseY, delta)
	}

	override fun drawSubtitle(ctx: GuiGraphicsExtractor, sub: String, sx: Int, sy: Int, maxW: Int) {
		val lines = font.split(Component.literal(sub), maxW.coerceAtLeast(20))
		lines.forEachIndexed { i, l -> ctx.text(font, l, sx, sy + i * 9, dev.ashtonite365.apex.features.menus.MenuTheme.GREY) }
		subtitleExtraLines = (lines.size - 1).coerceAtLeast(0)
	}
	override fun parse(title: String, slots: List<SlotText>) = WardrobeParser.parse(title, slots)
	override fun title(m: WardrobeModel) = "Wardrobe"
	override fun nav(m: WardrobeModel) = m.nav
	override fun subtitle(m: WardrobeModel) = m.sets.firstOrNull { it.state == SetState.EQUIPPED }?.let { "§aEquipped: §fSet ${it.number}" } ?: "§8No set equipped"

	override fun showsInventory(m: WardrobeModel) = true

	override fun inventoryScale() = dev.ashtonite365.apex.features.menus.style.WardrobeLayout.INVENTORY_SCALE

	override fun drawBody(ctx: GuiGraphicsExtractor, m: WardrobeModel, mx: Int, my: Int, top: Int, bottom: Int): Boolean {
		val p = palette()
		val gridTop = top + subtitleExtraLines * 9
		val L = dev.ashtonite365.apex.features.menus.style.WardrobeLayout.armour(x, w, gridTop, bottom)
		val equipped = m.sets.firstOrNull { it.state == SetState.EQUIPPED }?.index
		val keys = WardrobeKeybinds.TConfig.slotKeysEnabled
		var tip: List<Component>? = null
		for (c in WardrobeParser.cells(m.slots)) {
			val piece = c.kind == WardrobeParser.CellKind.PIECE
			val (cx, cy) = if (piece) L.cellAt(c.row, c.col) else L.buttonAt(c.col)
			val size = if (piece) L.cell else dev.ashtonite365.apex.features.menus.style.WardrobeLayout.BUTTON
			if (cy + size > bottom) continue
			val hov = MenuTheme.inside(mx, my, cx, cy, size, size)
			MenuTheme.frame(ctx, cx, cy, cx + size, cy + size, if (hov) p.fillHover else p.fill,
				if (c.col == equipped) p.accent else if (hov) p.borderHover else p.border)
			val st = screen.menu.slots.getOrNull(c.slot)?.item?.takeUnless { it.isEmpty }
			val slot = c.slot

			if (piece) {
				if (realSlotCell(slot, cx, cy)) session?.let { s ->
					val (ix, iy) = L.itemPos(cx, cy)
					s.realCells[slot] = ix to iy; s.cellScales[slot] = L.itemScale
				}
				continue
			}
			st?.let {

				val bs = (size - 2) / 16f
				ctx.pose().pushMatrix(); ctx.pose().translate(cx + 1f, cy + 1f); ctx.pose().scale(bs, bs)
				ctx.item(it, 0, 0); ctx.itemDecorations(font, it, 0, 0)
				ctx.pose().popMatrix()
			}
			area(cx, cy, size, size) { b -> clickSlot(slot, b) }
			if (hov) {
				val key = if (keys && c.kind == WardrobeParser.CellKind.SET_BUTTON)
					WardrobeKeybinds.TConfig.slotKeybinds.getOrNull(c.col)?.let { TileKeys.label(it.get()) }?.takeIf { it.isNotEmpty() } else null

				val value = if (c.kind == WardrobeParser.CellKind.SET_BUTTON) setValueLines(m, c.col) else emptyList()
				tip = (st?.let { Screen.getTooltipFromItem(Client.game, it) } ?: emptyList()) + listOfNotNull(key?.let { Component.literal("§8Key: §7$it") }) +
					value.map { Component.literal(it) }
			}
		}
		if (screen.menu.carried.isEmpty) tip?.takeIf { it.isNotEmpty() }?.let { ctx.setComponentTooltipForNextFrame(font, it, screenMouseX, screenMouseY) }
		return true
	}

	private fun setValueLines(m: WardrobeModel, col: Int): List<String> {
		val set = m.sets.firstOrNull { it.index == col } ?: return emptyList()
		val pieces = (0..3).mapNotNull { r ->
			screen.menu.slots.getOrNull(r * 9 + col)?.item?.takeUnless { it.isEmpty || WardrobeParser.armour(m.slots.associateBy { s -> s.slot }, col)[r] == null }
		}.map { st -> WardrobeValue.Piece(st.hoverName.string.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, ""),
			runCatching { dev.ashtonite365.apex.features.inventory.ItemValue.estimate(st)?.total }.getOrNull()) }
		return WardrobeValue.wrap(WardrobeValue.lines(set.number, pieces), (screen.width / 3).coerceIn(150, 260)) { font.width(it) }
	}

	override fun groups(m: WardrobeModel): List<TileGroup> {
		val keys = WardrobeKeybinds.TConfig.slotKeysEnabled
		fun tile(s: WardrobeSet) = Tile(s.buttonSlot, "Set ${s.number}",
			listOf(when (s.state) { SetState.EQUIPPED -> "§aEquipped"; SetState.READY -> "§eReady"; SetState.EMPTY -> "§8Empty"; SetState.LOCKED -> "§cLocked" }) +
				if (WardrobeSettings.showPieces && s.pieces.isNotEmpty()) listOf("§7" + s.pieces.joinToString(", ")) else emptyList(),
			s.state == SetState.EQUIPPED,
			if (keys) WardrobeKeybinds.TConfig.slotKeybinds.getOrNull(s.index)?.let { TileKeys.label(it.get()) } ?: "" else "")
		val (usable, other) = m.sets.partition { it.state == SetState.EQUIPPED || it.state == SetState.READY }
		return listOf(TileGroup("Sets", usable.map(::tile)), TileGroup("Empty / Locked", other.map(::tile))).filter { it.tiles.isNotEmpty() }
	}

	override fun keyPressed(input: KeyEvent): Boolean {
		if (super.keyPressed(input)) return true
		val m = currentModel() ?: return false
		val c = WardrobeKeybinds.TConfig
		if (!c.slotKeysEnabled) return false
		val bindings = c.slotKeybinds.mapIndexed { i, k -> k.get() to WardrobeParser.keySlot(m, i, c.unequipOnRepress) } +
			listOf(c.previousPage to m.nav.prev, c.nextPage to m.nav.next, c.changePageKeybind to (m.nav.next ?: m.nav.prev))
		return routeKey(input, bindings)
	}
}
