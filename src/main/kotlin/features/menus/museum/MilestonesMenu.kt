// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.museum

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
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.museum.MuseumInventoryAccess
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.ApexClickDelay

@Config
object MilestonesMenuSettings : MenuSettings("museum-milestones") {

}

object MilestonesMenu : CustomMenu {
	override val id = "museum-milestones"
	override val displayName = "Museum Milestones"
	override val title = MilestonesParser.TITLE
	override val settings get() = MilestonesMenuSettings
	override val defaultSize = 380 to 272

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(this) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = MilestonesView(screen)

	fun colourOf(itemPath: String): TileColour? = when {
		"lime" in itemPath || "green" in itemPath -> TileColour.GREEN
		"yellow" in itemPath -> TileColour.YELLOW
		"red" in itemPath -> TileColour.RED
		else -> null
	}
}

object MilestonesLayout {
	const val CELL = 22
	fun cols(width: Int) = maxOf(1, minOf(10, width / CELL))
	fun rows(n: Int, cols: Int) = (n + cols - 1) / cols
	fun maxScroll(n: Int, cols: Int, visibleRows: Int) = maxOf(0, rows(n, cols) - visibleRows)
}

class MilestonesView(screen: AbstractContainerScreen<*>) : ApexViewBase<MilestonesPage>(screen) {
	private val c get() = MilestonesMenuSettings.customisation

	private val gridScroll = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey() }
	override fun palette() = c.palette

	private fun parsePage(title: String, slots: List<SlotText>): MilestonesPage? {
		val colours = stacks.mapNotNull { (slot, st) -> MilestonesMenu.colourOf(BuiltInRegistries.ITEM.getKey(st.item).path)?.let { slot to it } }.toMap()
		return MilestonesParser.parse(title, slots, colours)?.also { MilestoneCache.recordPage(it) }
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: MilestonesPage, mx: Int, my: Int) {
		val p = palette()
		val pad = 6
		ctx.text(font, "§6§lMuseum Milestones", x + pad, y + 7, MenuTheme.GOLD)
		val vw = font.width("Vanilla") + 10

		val cache = MilestoneCache.data()
		val prog = m.progress ?: cache?.progress
		val reached = prog?.milestone
		var ty = y + 22
		val xpText = listOfNotNull(prog?.xp?.let { xp -> "§bTotal XP §f%,d".format(xp) + (prog.xpMax?.let { "§7/§f%,d".format(it) } ?: "") },
			reached?.let { "§7Milestone §a$it§7/§f${prog.milestones ?: MilestonesParser.TOTAL}" },
			prog?.nextPct?.let { "§7next §e$it%" }).joinToString(" §8· ").ifEmpty { "§8Open the museum page to read your XP" }
		ctx.text(font, MenuTheme.fit(xpText, w - 2 * pad, font::width), x + pad, ty, MenuTheme.WHITE)
		ty += 11
		val bw = w - 2 * pad
		ctx.fill(x + pad, ty, x + pad + bw, ty + 4, 0xFF2A2A2A.toInt())
		val f = if (prog?.xp != null && (prog.xpMax ?: 0) > 0) (prog.xp.toDouble() / prog.xpMax!!).coerceIn(0.0, 1.0) else 0.0
		ctx.fill(x + pad, ty, x + pad + (bw * f).toInt(), ty + 4, 0xFF55FFFF.toInt())
		ty += 9

		val showInv = MuseumInventoryAccess.shows(MuseumInventoryAccess.enabled, screen.menu.slots.size)
		val invH = if (showInv) 4 * 18 + 4 + 12 else 0
		val btnY = y + h - pad - 16 - invH - (if (showInv) 4 else 0)
		val gridTop = ty; val gridH = btnY - 4 - gridTop
		val cols = MilestonesLayout.cols(w - 2 * pad)

		val view = dev.ashtonite365.apex.features.menus.MenuBounds(x + 1, gridTop, w - 2, maxOf(0, gridH))
		gridScroll.layout(view, ((MilestonesParser.TOTAL + cols - 1) / cols) * MilestonesLayout.CELL - 2)
		val live = m.tiles.associateBy { it.level }
		val liveStates = (cache?.states.orEmpty() + m.tiles.mapNotNull { t -> t.state?.let { t.level to it } })
		val states = MilestonesParser.states(reached, liveStates)
		val ox = x + pad + ((w - 2 * pad) - cols * MilestonesLayout.CELL) / 2
		var tip: List<String>? = null
		beginClip(ctx, view)
		for (lv in 1..MilestonesParser.TOTAL) {
			val cy = gridTop + ((lv - 1) / cols) * MilestonesLayout.CELL - gridScroll.offset
			if (cy + MilestonesLayout.CELL <= view.y || cy >= view.y + view.h) continue
			val cx = ox + ((lv - 1) % cols) * MilestonesLayout.CELL
			val st = states[lv - 1]
			val tint = when (st) { MilestoneState.DONE -> 0xFF1E4A28.toInt(); MilestoneState.IN_PROGRESS -> 0xFF5A4A14.toInt(); MilestoneState.LOCKED -> 0xFF4A1A1A.toInt() }
			val hover = hovered(mx, my, cx, cy, MilestonesLayout.CELL - 2, MilestonesLayout.CELL - 2)
			ctx.fill(cx, cy, cx + MilestonesLayout.CELL - 2, cy + MilestonesLayout.CELL - 2, if (hover) p.borderHover else p.border)
			ctx.fill(cx + 1, cy + 1, cx + MilestonesLayout.CELL - 3, cy + MilestonesLayout.CELL - 3, tint)
			val lt = "$lv"
			val col = when (st) { MilestoneState.DONE -> 0xFF55FF55.toInt(); MilestoneState.IN_PROGRESS -> 0xFFFFFF55.toInt(); MilestoneState.LOCKED -> 0xFFFF5555.toInt() }
			ctx.text(font, lt, cx + (MilestonesLayout.CELL - 2 - font.width(lt)) / 2, cy + 6, col)
			if (MilestoneRewards.extras(lv).isNotEmpty()) ctx.fill(cx + MilestonesLayout.CELL - 6, cy + 2, cx + MilestonesLayout.CELL - 4, cy + 4, MenuTheme.GOLD)
			area(cx, cy, MilestonesLayout.CELL - 2, MilestonesLayout.CELL - 2) { b -> if (b == 0) onTile(lv, m) }
			if (hover) tip = MilestoneRewards.tooltip(lv, live[lv]?.lore ?: cache?.lore?.get(lv)) +
				listOf(if (lv in live) "§eClick to open/claim" else "§8Scroll Hypixel to reach") +
				listOf(when (st) { MilestoneState.DONE -> "§aDone"; MilestoneState.IN_PROGRESS -> "§eIn progress" + (prog?.nextPct?.let { " ($it%)" } ?: ""); MilestoneState.LOCKED -> "§cLocked" })
		}

		endClip(ctx)

		var bx = x + pad
		m.backSlot?.let { s -> button(ctx, mx, my, bx, btnY, 60, 16, "‹ Go Back") { _ -> clickSlot(s) }; bx += 63 }
		m.closeSlot?.let { s -> button(ctx, mx, my, bx, btnY, 50, 16, "Close") { _ -> clickSlot(s) }; bx += 53 }

		if (showInv) MuseumInventoryAccess.guard("milestones panel") {
			val (ix, iy) = MuseumInventoryAccess.panelOrigin(x, y, w, h, pad)
			inventoryPanel(ctx, mx, my, ix, iy)
		}
		tip?.let { t -> ctx.setComponentTooltipForNextFrame(font, t.map { Component.literal(it) }, mx, my) }
	}

	private fun onTile(lv: Int, m: MilestonesPage) {
		MilestoneClaim.decide(lv, m)?.let { clickSlot(it, 0) }
	}

	override fun parse(title: String, slots: List<SlotText>): MilestonesPage? = parsePage(title, slots)

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		gridScroll.scroll(amount)
		return true
	}
}
