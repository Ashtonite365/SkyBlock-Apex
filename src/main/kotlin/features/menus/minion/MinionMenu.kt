// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.minion

import dev.ashtonite365.apex.util.bazaarStockOf
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.repo.HypixelStaticData
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

@Config
object MinionMenuSettings : MenuSettings("minion", panels = listOf("value", "effects")) {
	val showInventory by toggle("show-inventory") { true }
}

@Config
object IdealLayoutMenuSettings : MenuSettings("minion-ideal-layout")

object MinionMenu : CustomMenu {
	override val id = "minion"
	override val displayName = "Minion"

	override val title = MinionCraftParser.TITLE_HINT
	override val settings get() = MinionMenuSettings
	override val defaultSize = 340 to 262

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		MenuRegistry.register(this)
		MenuRegistry.register(IdealLayoutMenu)
	}

	override fun create(screen: AbstractContainerScreen<*>): MenuView? =
		if (screen.menu.slots.size >= MinionSlotMap.CHEST_SLOTS + 36) MinionRouterView(screen) else null
}

object IdealLayoutMenu : CustomMenu {
	override val id = "minion-ideal-layout"
	override val displayName = "Minion: Ideal Layout"
	override val title = Regex("^" + Regex.escape(IdealLayoutSlotMap.TITLE) + "$")
	override val settings get() = IdealLayoutMenuSettings
	override val defaultSize = 230 to 200
	override fun create(screen: AbstractContainerScreen<*>): MenuView? =
		if (screen.menu.slots.size >= IdealLayoutSlotMap.CHEST_SLOTS) IdealLayoutView(screen) else null
}

abstract class MinionViewBase<M : Any>(screen: AbstractContainerScreen<*>) : ApexViewBase<M>(screen) {
	private var shift = false
	private var tooltip: ItemStack? = null

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		shift = click.hasShiftDown()
		return super.mouseClicked(click, doubled)
	}

	protected fun forward(slot: Int, button: Int, realItem: Boolean = false) {
		val menu = screen.menu
		val player = Client.player ?: return
		if (!windowStillOpen()) return
		val s = menu.slots.getOrNull(slot) ?: return
		val (b, kind) = MinionClicks.route(button, shift)

		val input = if (kind == MinionClicks.Kind.QUICK_MOVE) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP
		if (dev.ashtonite365.apex.util.ApexClickDelay.isProtected(menu, s, input)) return
		Client.gameMode?.handleContainerInput(menu.containerId, slot, b, input, player)
	}

	protected fun live(slot: Int): ItemStack = screen.menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

	protected fun tile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, slot: Int, highlight: Int? = null, realItem: Boolean = true) {
		val hover = MenuTheme.inside(mx, my, tx, ty, 18, 18)
		MenuTheme.frame(ctx, tx, ty, tx + 18, ty + 18, if (hover) MenuTheme.FILL_HOVER else MenuTheme.FILL, highlight ?: MenuTheme.BORDER)
		val st = live(slot)
		if (!st.isEmpty) {
			ctx.item(st, tx + 1, ty + 1)
			ctx.itemDecorations(font, st, tx + 1, ty + 1)
			if (hover) tooltip = st
		}
		area(tx, ty, 18, 18) { b -> forward(slot, b, realItem) }
	}

	protected fun realTile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, slot: Int, highlight: Int? = null) {
		val hover = MenuTheme.inside(mx, my, tx, ty, 18, 18)
		MenuTheme.frame(ctx, tx, ty, tx + 18, ty + 18, if (hover) MenuTheme.FILL_HOVER else MenuTheme.FILL, highlight ?: MenuTheme.BORDER)
		realSlotCell(slot, tx, ty)
	}

	protected fun inventory(ctx: GuiGraphicsExtractor, mx: Int, my: Int, ix: Int, iy: Int, chestSlots: Int) {
		ctx.text(font, "§7Inventory", ix, iy - 10, MenuTheme.GREY)
		for (row in 0..3) for (col in 0..8)
			realTile(ctx, mx, my, ix + col * 18, iy + row * 18 + (if (row == 3) 4 else 0), MinionSlotMap.inventorySlot(row, col, chestSlots))
	}

	protected fun finish(ctx: GuiGraphicsExtractor, mx: Int, my: Int) {

		if (screen.menu.carried.isEmpty) tooltip?.let { ctx.setTooltipForNextFrame(font, it, mx, my) }
		tooltip = null
	}
}

class MinionView(screen: AbstractContainerScreen<*>) : MinionViewBase<MinionModel>(screen), MenuView {
	private val c get() = MinionMenuSettings.customisation
	override fun palette() = c.palette
	override fun parse(title: String, slots: List<SlotText>) = MinionParser.parse(title, slots)

	private fun bazaarSell(id: String): Double? =
		HypixelStaticData.bazaarData[bazaarStockOf(ItemId(id))]?.quickStatus?.sellPrice?.takeIf { it > 0 }

	override fun draw(ctx: GuiGraphicsExtractor, m: MinionModel, mx: Int, my: Int) {
		val p = palette(); val pad = 6
		ctx.text(font, "§6§l${m.type} Minion ${m.tierRoman}", x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val info = listOfNotNull(m.timeBetweenActions?.let { "§7Every §f$it" }, m.maxStorage?.let { "§7Max storage §f${MenuText.coins(it.toDouble())}" },
			m.resourcesGenerated?.let { "§7Generated §f${MenuText.coins(it.toDouble())}" }).joinToString(" §8· ")
		ctx.text(font, MenuTheme.fit(info, w - 2 * pad, font::width), x + pad, y + 19, MenuTheme.GREY)

		val lx = x + pad; var ly = y + 32; val lw = 150
		MenuTheme.panel(ctx, lx, ly, lw, 5 * 21 + 6, p)
		ly += 4
		for (s in m.itemSlots) {
			tile(ctx, mx, my, lx + 4, ly, s.kind.slot, if (s.filled) MenuTheme.GOLD else null)
			ctx.text(font, "§f${s.kind.label}", lx + 26, ly + 1, MenuTheme.WHITE)
			val sub = if (s.filled) (if (c.shows("effects") && s.effect != null) "§a${s.effect}" else "§7${s.name}") else "§8${s.kind.hint}"
			ctx.text(font, MenuTheme.fit(sub, lw - 30, font::width), lx + 26, ly + 10, MenuTheme.GREY)
			ly += 21
		}

		val rx = lx + lw + 6; val rw = x + w - pad - rx; var ry = y + 32
		MenuTheme.panel(ctx, rx, ry, rw, 76, p)

		val cells = MinionPanes.layout(MinionSlotMap.STORAGE) { MinionPanes.hide(live(it)) }
		val shown = cells.map { it.first }
		ctx.text(font, "§fStorage §7(${shown.count { !live(it).isEmpty }}/${shown.size} slots)", rx + 5, ry + 4, MenuTheme.WHITE)
		for ((s, col, row) in cells) realTile(ctx, mx, my, rx + 5 + col * 18, ry + 15 + row * 18, s)
		if (c.shows("value")) {
			val v = MinionClicks.value(shown.map(::live).filter { !it.isEmpty }.map { it.view().repoItemId?.skyblockId to it.count }, ::bazaarSell)
			val vx = rx + 5 + 5 * 18 + 6
			ctx.text(font, "§7Value", vx, ry + 16, MenuTheme.GREY)
			ctx.text(font, "§6" + MenuText.coins(v.coins), vx, ry + 27, MenuTheme.GOLD)
			ctx.text(font, MenuTheme.fit("§8bazaar sell" + (if (v.unpriced > 0) ", ${v.unpriced} unpriced" else ""), rw - (vx - rx) - 4, font::width), vx, ry + 38, MenuTheme.DIM)
		}
		if (m.compactors.isNotEmpty()) ctx.text(font, MenuTheme.fit("§d" + m.compactors.joinToString(", "), rw - 10, font::width), rx + 5, ry + 68 - 2, MenuTheme.WHITE)
		ry += 80
		MenuTheme.panel(ctx, rx, ry, rw, 57, p)
		val bw = (rw - 15) / 2
		button(ctx, mx, my, rx + 5, ry + 4, bw, 14, "Collect All") { b -> forward(m.collectAll.slot, b) }
		button(ctx, mx, my, rx + 10 + bw, ry + 4, bw, 14, "§cPickup Minion") { b -> forward(m.pickup.slot, b) }
		m.idealLayout?.let { b0 -> button(ctx, mx, my, rx + 5, ry + 21, bw, 14, "Ideal Layout") { b -> forward(b0.slot, b) } }
		m.quickUpgrade?.let { b0 -> button(ctx, mx, my, rx + 10 + bw, ry + 21, bw, 14, "Quick Upgrade", enabled = true) { b -> forward(b0.slot, b) } }
		m.nextTier?.let { b0 ->
			button(ctx, mx, my, rx + 5, ry + 38, bw, 14, "Next Tier") { b -> forward(b0.slot, b) }
			val cost = if (m.maxTier) "§8Max tier reached" else m.upgradeCost.firstOrNull()?.let { "§e$it" } ?: "§8See Hypixel's tooltip"
			ctx.text(font, MenuTheme.fit(cost, bw, font::width), rx + 10 + bw, ry + 42, MenuTheme.GREY)
		}

		var ox = lx + 4
		val oy = y + 32 + 5 * 21 + 10
		for (o in m.others) { if (MinionPanes.hide(live(o.slot))) continue; tile(ctx, mx, my, ox, oy, o.slot); ox += 20 }

		if (MinionMenuSettings.showInventory) inventory(ctx, mx, my, x + (w - 162) / 2, y + h - 80, MinionSlotMap.CHEST_SLOTS)
		finish(ctx, mx, my)
	}
}

class IdealLayoutView(screen: AbstractContainerScreen<*>) : MinionViewBase<IdealLayoutModel>(screen), MenuView {
	override fun palette() = IdealLayoutMenuSettings.customisation.palette
	override fun parse(title: String, slots: List<SlotText>) = IdealLayoutParser.parse(title, slots)

	override fun draw(ctx: GuiGraphicsExtractor, m: IdealLayoutModel, mx: Int, my: Int) {
		val pad = 6
		ctx.text(font, "§6§l" + IdealLayoutSlotMap.TITLE, x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		ctx.text(font, MenuTheme.fit("§7${m.minion.name} in the centre", w - 2 * pad, font::width), x + pad, y + 19, MenuTheme.GREY)
		val size = 22; val gx = x + (w - 5 * size) / 2; val gy = y + 32
		MenuTheme.panel(ctx, gx - 4, gy - 4, 5 * size + 8, 5 * size + 8, palette())
		IdealLayoutSlotMap.GRID.forEachIndexed { i, s ->
			tile(ctx, mx, my, gx + (i % 5) * size + 2, gy + (i / 5) * size + 2, s, if (s == IdealLayoutSlotMap.CENTRE) MenuTheme.GOLD else null)
		}
		var ox = x + pad
		for (o in m.others) { tile(ctx, mx, my, ox, y + h - 40, o.slot); ox += 20 }
		val by = y + h - 20; val bw = 70
		m.goBack?.let { b0 -> button(ctx, mx, my, x + w / 2 - bw - 3, by, bw, 14, "‹ Go Back") { b -> forward(b0.slot, b) } }
		m.close?.let { b0 -> button(ctx, mx, my, x + w / 2 + 3, by, bw, 14, "Close") { b -> forward(b0.slot, b) } }
		finish(ctx, mx, my)
	}
}
