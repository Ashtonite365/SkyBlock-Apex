// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.garden

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuBounds
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.features.menus.support.InventoryPanel
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.ScrollView
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config

data class GardenTile(val slot: Int, val name: String, val lore: List<String>)

data class GardenPage(val title: String, val tiles: List<GardenTile>, val controls: List<GardenTile>, val nav: List<GardenTile>)

data class GardenPageSpec(val id: String, val displayName: String, val title: Regex, val required: List<String>, val columns: Int,
                          val showInventory: Boolean = false, val defaultSize: Pair<Int, Int> = 360 to 240,

                          val compact: Boolean = false)

object GardenPageParser {

	val NAV = listOf("Go Back", "Previous Page", "Next Page", "Close")

	fun parse(spec: GardenPageSpec, title: String, slots: List<SlotText>): GardenPage? {
		val t = MenuText.clean(title)
		if (!spec.title.matches(t)) return null
		val named = slots.filter { it.cleanName.isNotBlank() }.sortedBy { it.slot }
		if (named.isEmpty()) return null
		if (spec.required.any { r -> named.none { it.cleanName.equals(r, true) } }) return null
		val lastRow = named.maxOf { it.slot } / 9
		fun tile(s: SlotText) = GardenTile(s.slot, s.cleanName, s.cleanLore)
		val nav = named.filter { s -> NAV.any { it.equals(s.cleanName, true) } }
		val rest = named - nav.toSet()
		val controls = if (lastRow > 0) rest.filter { it.slot / 9 == lastRow } else emptyList()
		val tiles = rest - controls.toSet()
		if (tiles.isEmpty() && controls.isEmpty()) return null
		return GardenPage(t, tiles.map(::tile), controls.map(::tile), nav.sortedBy { n -> NAV.indexOfFirst { it.equals(n.cleanName, true) } }.map(::tile))
	}

	fun body(tile: GardenTile, maxWidth: Int, width: (String) -> Int): List<String> =
		tile.lore.flatMap { if (it.isBlank()) listOf("") else EnchantLabel.wrap(it, maxWidth, width) }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
}

object GardenPageSpecs {
	val DESK = GardenPageSpec("garden-desk", "Garden Desk", Regex("^Desk$"), listOf("Configure Plots"), 4, defaultSize = 360 to 200, compact = true)
	val CROP_UPGRADES = GardenPageSpec("garden-crop-upgrades", "Crop Upgrades", Regex("^Crop Upgrades$"), emptyList(), 5, defaultSize = 560 to 340, compact = true)
	val GARDEN_MILESTONES = GardenPageSpec("garden-milestones", "Garden Milestones", Regex("^Garden Milestones$"), emptyList(), 2, defaultSize = 300 to 160, compact = true)
	val CROP_MILESTONES = GardenPageSpec("garden-crop-milestones", "Crop Milestones", Regex("^Crop Milestones$"), emptyList(), 5, defaultSize = 600 to 400, compact = true)

	val VISITOR_MILESTONES = GardenPageSpec("garden-visitor-milestones", "Visitor Milestones", Regex("^Visitor Milestones$"), emptyList(), 2, defaultSize = 360 to 220, compact = true)
	val VISITOR_LOGBOOK = GardenPageSpec("garden-visitor-logbook", "Visitor's Logbook", Regex("^\\(\\d+/\\d+\\) Visitor's Logbook$"), emptyList(), 7, defaultSize = 480 to 300)
	val SKYMART = GardenPageSpec("garden-skymart", "SkyMart", Regex("^SkyMart$"), emptyList(), 5, defaultSize = 420 to 170, compact = true)
	val GARDEN_SKINS = GardenPageSpec("garden-skins", "Garden Skins", Regex("^Garden Skins$"), emptyList(), 2, defaultSize = 280 to 140, compact = true)
	val FARMING_TOOLKIT = GardenPageSpec("garden-farming-toolkit", "Farming Toolkit", Regex("^Farming Toolkit$"), emptyList(), 6, showInventory = true, defaultSize = 480 to 380, compact = true)
}

@Config object GardenDeskSettings : MenuSettings("garden-desk") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenCropUpgradesSettings : MenuSettings("garden-crop-upgrades") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenMilestonesSettings : MenuSettings("garden-milestones") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenCropMilestonesSettings : MenuSettings("garden-crop-milestones") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenVisitorMilestonesSettings : MenuSettings("garden-visitor-milestones") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenVisitorLogbookSettings : MenuSettings("garden-visitor-logbook") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenSkyMartSettings : MenuSettings("garden-skymart") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenSkinsSettings : MenuSettings("garden-skins") { val reset by button("reset") { resetToDefaults() } }
@Config object GardenFarmingToolkitSettings : MenuSettings("garden-farming-toolkit") { val reset by button("reset") { resetToDefaults() } }

open class GardenPageMenu(val spec: GardenPageSpec, private val settingsRef: () -> MenuSettings) : CustomMenu {
	override val id get() = spec.id
	override val displayName get() = spec.displayName
	override val title get() = spec.title
	override val settings get() = settingsRef()
	override val defaultSize get() = spec.defaultSize
	override fun create(screen: AbstractContainerScreen<*>): MenuView = GardenPageView(screen, spec, settingsRef)
}

object GardenDeskMenu : GardenPageMenu(GardenPageSpecs.DESK, { GardenDeskSettings })
object GardenCropUpgradesMenu : GardenPageMenu(GardenPageSpecs.CROP_UPGRADES, { GardenCropUpgradesSettings })
object GardenMilestonesMenu : GardenPageMenu(GardenPageSpecs.GARDEN_MILESTONES, { GardenMilestonesSettings })
object GardenCropMilestonesMenu : GardenPageMenu(GardenPageSpecs.CROP_MILESTONES, { GardenCropMilestonesSettings })
object GardenVisitorMilestonesMenu : GardenPageMenu(GardenPageSpecs.VISITOR_MILESTONES, { GardenVisitorMilestonesSettings })
object GardenVisitorLogbookMenu : GardenPageMenu(GardenPageSpecs.VISITOR_LOGBOOK, { GardenVisitorLogbookSettings })
object GardenSkyMartMenu : GardenPageMenu(GardenPageSpecs.SKYMART, { GardenSkyMartSettings })
object GardenSkinsMenu : GardenPageMenu(GardenPageSpecs.GARDEN_SKINS, { GardenSkinsSettings })
object GardenFarmingToolkitMenu : GardenPageMenu(GardenPageSpecs.FARMING_TOOLKIT, { GardenFarmingToolkitSettings })

object GardenMenus {
	val ALL: List<CustomMenu> get() = listOf(GardenDeskMenu, GardenPlotsMenu, GardenCropUpgradesMenu, GardenMilestonesMenu, GardenCropMilestonesMenu,
		GardenVisitorMilestonesMenu, GardenVisitorLogbookMenu, GardenSkyMartMenu, GardenSkinsMenu, GardenFarmingToolkitMenu)

	@Subscribe
	fun onInit(event: ClientInitEvent) { ALL.forEach(MenuRegistry::register) }
}

class GardenPageView(screen: AbstractContainerScreen<*>, private val spec: GardenPageSpec, private val settingsRef: () -> MenuSettings)
	: ApexViewBase<GardenPage>(screen) {
	override fun palette() = settingsRef().customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = GardenPageParser.parse(spec, title, slots)
	override val logTag get() = "[ApexGarden] ${spec.displayName}"
	private val scroll = ScrollView { scrollKey(spec.id) }
	override val scrollView get() = scroll
	private var planKey: Any? = null
	private var plan: FitPlan? = null

	override fun draw(ctx: GuiGraphicsExtractor, m: GardenPage, mx: Int, my: Int) {
		ctx.text(font, "§a§l${m.title}", x + 6, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val footer = 20
		val inv = if (spec.showInventory) InventoryPanel.HEIGHT + 6 else 0
		val view = MenuBounds(x + 4, y + 20, w - 8, h - 20 - footer - inv)
		var tip: net.minecraft.world.item.ItemStack? = null
		val cy = y + 20 - scroll.offset
		var used = 0
		if (spec.compact) {

			val crops = spec.id == GardenPageSpecs.CROP_MILESTONES.id

			val all: List<GardenTile?> = if (crops) CropCards.cells(m.tiles, m.controls) else m.tiles + m.controls
			val fit = all.map { t -> t?.let { FitTile("§f" + it.name, (if (crops) CropCards.body(it.lore) else it.lore).map { l -> if (l.isBlank()) "" else "§7$l" }) } }
			val key = Triple(fit, view.w, view.h)
			val fixedCols = if (crops) CropCards.COLS else if (spec.id == GardenPageSpecs.VISITOR_MILESTONES.id && m.tiles.size == 2) 2 else null
			val p = (if (key == planKey) plan else null) ?: (GardenFit.plan(fit, view.w, view.h, font::width, fixedCols, prefer = spec.columns, allowHideBody = !crops)
				?: GardenFit.plan(fit, view.w, Int.MAX_VALUE, font::width, fixedCols ?: all.size, iconW = 18)!!).also { planKey = key; plan = it }
			var ty = view.y
			for ((r, row) in all.chunked(p.cols).withIndex()) {
				val rh = p.rowHeights[r]
				for ((i, t) in row.withIndex()) {
					if (t == null) continue
					val tx = view.x + i * (p.tileW + p.gap)
					val hov = hovered(mx, my, tx, ty, p.tileW, rh)
					val pal = palette()
					MenuTheme.frame(ctx, tx, ty, tx + p.tileW, ty + rh, if (hov) pal.fillHover else pal.fill, if (hov) pal.borderHover else pal.border)
					stacks[t.slot]?.let { ctx.item(it, tx + 1, ty + 1); if (hov) tip = it }
					GardenFit.drawLines(ctx, font, GardenFit.lines(fit[all.indexOf(t)]!!, p.textW, p.showBody, font::width), tx + 2 + p.iconW, ty + 3, p.scale, MenuTheme.WHITE)
					area(tx, ty, p.tileW, rh) { b -> clickSlot(t.slot, b) }
				}
				ty += rh + p.gap
			}
		} else {
		beginClip(ctx, view)
		used += grid(ctx, mx, my, m.tiles, spec.columns, view.x, cy + used, view.w) { tip = it }
		if (m.controls.isNotEmpty()) { used += 4; used += grid(ctx, mx, my, m.controls, maxOf(m.controls.size, 1).coerceAtMost(6), view.x, cy + used, view.w) { tip = it } }
		endClip(ctx)
		scroll.layout(view, used)
		}

		if (spec.showInventory) inventoryPanel(ctx, mx, my, x + (w - InventoryPanel.WIDTH) / 2, y + h - footer - inv + 2)

		var fx = x + 4
		for (n in m.nav) {
			val bw = font.width(n.name) + 12
			val bx = if (n.name.equals("Close", true)) x + (w - bw) / 2 else fx.also { fx += bw + 4 }
			button(ctx, mx, my, bx, y + h - 18, bw, 14, n.name) { b -> clickSlot(n.slot, b) }
		}
		tip?.let { if (screen.menu.carried.isEmpty) ctx.setTooltipForNextFrame(font, it, mx, my) }
	}

	private fun grid(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tiles: List<GardenTile>, cols: Int, gx: Int, gy: Int, gw: Int,
	                 onHover: (net.minecraft.world.item.ItemStack) -> Unit): Int {
		if (tiles.isEmpty()) return 0
		val c = cols.coerceIn(1, tiles.size)
		val tw = (gw - (c - 1) * 4) / c
		var yy = gy
		for (row in tiles.chunked(c)) {
			val bodies = row.map { GardenPageParser.body(it, tw - 8, font::width) }
			val names = row.map { EnchantLabel.wrap(it.name, tw - 26, font::width) }
			val th = row.indices.maxOf { 6 + maxOf(18, names[it].size * 10) + bodies[it].size * 10 + 4 }
			for ((i, t) in row.withIndex()) {
				val tx = gx + i * (tw + 4)
				val hov = hovered(mx, my, tx, yy, tw, th)
				val p = palette()
				MenuTheme.frame(ctx, tx, yy, tx + tw, yy + th, if (hov) p.fillHover else p.fill, if (hov) p.borderHover else p.border)
				stacks[t.slot]?.let { ctx.item(it, tx + 4, yy + 4); if (hov) onHover(it) }
				names[i].forEachIndexed { k, l -> ctx.text(font, "§f$l", tx + 24, yy + 6 + k * 10, MenuTheme.WHITE) }
				val by = yy + 6 + maxOf(18, names[i].size * 10)
				bodies[i].forEachIndexed { k, l -> ctx.text(font, "§7$l", tx + 4, by + k * 10, MenuTheme.GREY) }
				area(tx, yy, tw, th) { b -> clickSlot(t.slot, b) }
			}
			yy += th + 4
		}
		return yy - gy
	}
}
