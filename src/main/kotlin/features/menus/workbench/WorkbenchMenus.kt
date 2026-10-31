// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.workbench

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.ItemStack
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.inventory.AnvilReforgeHelpers
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuClicks
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuButtons
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.ApexClickDelay
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId

@Config
object BrewingStandMenuSettings : MenuSettings("brewing-stand")

@Config
object AnvilMenuSettings : MenuSettings("anvil")

enum class WorkbenchKind(val label: String) { BREWING_STAND("Brewing Stand"), ANVIL("Anvil") }

abstract class WorkbenchMenu(val kind: WorkbenchKind) : CustomMenu {
	override val defaultSize = 330 to 250
	override fun create(screen: AbstractContainerScreen<*>): MenuView? =
		if (screen.menu is ChestMenu && screen.menu.slots.size - 36 >= 9) WorkbenchView(screen, kind, settings) else null
}

object BrewingStandMenu : WorkbenchMenu(WorkbenchKind.BREWING_STAND) {
	override val id = "brewing-stand"
	override val displayName = "Brewing Stand"
	override val title = WorkbenchTitles.BREWING
	override val settings get() = BrewingStandMenuSettings

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		MenuRegistry.register(this)
		MenuRegistry.register(AnvilMenu)
	}
}

object AnvilMenu : WorkbenchMenu(WorkbenchKind.ANVIL) {
	override val id = "anvil"
	override val displayName = "Anvil"
	override val title = WorkbenchTitles.ANVIL
	override val settings get() = AnvilMenuSettings
}

class WorkbenchView(screen: AbstractContainerScreen<*>, private val kind: WorkbenchKind, private val settings: MenuSettings) :
	ApexViewBase<WorkbenchModel>(screen), MenuView {
	private val chestSlots get() = screen.menu.slots.size - 36
	private val memory = SocketMemory()
	private var shift = false
	private var tooltip: ItemStack? = null

	private class SlotRect(val x: Int, val y: Int, val slot: Int, val real: Boolean) {
		fun contains(mx: Double, my: Double) = mx >= x && mx < x + 18 && my >= y && my < y + 18
	}
	private val rects = mutableListOf<SlotRect>()
	private var lastMouse: Pair<Double, Double>? = null

	override fun hoveredRealSlot(): net.minecraft.world.inventory.Slot? {
		val (mx, my) = lastMouse ?: return null
		val r = rects.lastOrNull { it.real && it.contains(mx, my) } ?: return null
		return screen.menu.slots.getOrNull(r.slot)?.takeIf { it.container is net.minecraft.world.entity.player.Inventory }
	}

	override fun palette() = settings.customisation.palette

	override fun parse(title: String, slots: List<SlotText>): WorkbenchModel? {
		val n = chestSlots
		val seen = memory.update(screen.menu.containerId, n, stacks.keys.filter { it < n }.toSet())
		val ws = slots.map { t -> stacks[t.slot].let { st -> WorkbenchSlot.of(t, st?.let { isPane(it) }, st?.let { isRealItem(it) }) } }
		return WorkbenchParser.parse(title, n, ws, seen)
	}

	private fun isPane(st: ItemStack) = !st.isEmpty && BuiltInRegistries.ITEM.getKey(st.item).path.endsWith("glass_pane")
	private fun isRealItem(st: ItemStack) = !st.isEmpty && st.view().repoItemId != null

	private fun live(slot: Int): ItemStack = screen.menu.slots.getOrNull(slot)?.item ?: ItemStack.EMPTY

	private fun click(slot: Int, button: Int, real: Boolean) {
		if (!windowStillOpen()) return
		val menu = screen.menu
		val s = menu.slots.getOrNull(slot) ?: return
		val p = WorkbenchClicks.plan(slot, button, shift, real) ?: return
		if (p.silent) MenuClicks.click(menu, s, p.button)
		else ApexClickDelay.send(menu, s, p.button, if (p.input == WorkbenchClicks.Input.QUICK_MOVE) ContainerInput.QUICK_MOVE else ContainerInput.PICKUP)
	}

	private fun tile(ctx: GuiGraphicsExtractor, mx: Int, my: Int, tx: Int, ty: Int, slot: Int, real: Boolean, border: Int? = null,
	                 emptyTip: ItemStack? = null, overlay: Int? = null) {
		val hover = MenuTheme.inside(mx, my, tx, ty, 18, 18)
		MenuTheme.frame(ctx, tx, ty, tx + 18, ty + 18, if (hover) MenuTheme.FILL_HOVER else MenuTheme.FILL, border ?: MenuTheme.BORDER)
		val st = live(slot)
		if (!st.isEmpty) {
			ctx.item(st, tx + 1, ty + 1)
			ctx.itemDecorations(font, st, tx + 1, ty + 1)
			if (hover) tooltip = st
		} else if (hover && emptyTip != null && !emptyTip.isEmpty) tooltip = emptyTip
		overlay?.let { ctx.fill(tx + 1, ty + 1, tx + 17, ty + 17, it) }
		rects += SlotRect(tx, ty, slot, real)
		area(tx, ty, 18, 18) { b -> click(slot, b, real) }
	}

	override fun draw(ctx: GuiGraphicsExtractor, m: WorkbenchModel, mx: Int, my: Int) {
		rects.clear()
		val p = palette(); val pad = 6
		ctx.text(font, "§6§l" + MenuTheme.fit(m.title.ifEmpty { kind.label }, w / 2, font::width), x + pad, y + 7, MenuTheme.WHITE)
		val vw = font.width("Vanilla") + 10

		val colW = maxOf(162, m.cols * 22 + 8)
		val iy = y + h - 80
		val ay = y + 22
		val pitch = ((iy - 14 - ay - 8) / m.rows.coerceAtLeast(1)).coerceIn(18, 22)
		val aw = m.cols * pitch + 8; val ah = m.rows * pitch + 8
		val ax = x + pad + (colW - aw) / 2
		MenuTheme.panel(ctx, ax, ay, aw, ah, p)
		val off = (pitch - 18) / 2
		for (t in m.tiles) {
			val border = when (t.role) { WorkbenchRole.SOCKET -> p.accent; WorkbenchRole.ITEM -> MenuTheme.GOLD; else -> null }
			tile(ctx, mx, my, ax + 4 + t.col * pitch + off, ay + 4 + t.row * pitch + off, t.slot, t.role.realSlot, border,
				emptyTip = t.hint?.let { stacks[it] ?: live(it) })
		}

		val ix = x + pad + (colW - 162) / 2
		val left = if (kind == WorkbenchKind.ANVIL && AnvilReforgeHelpers.TConfig.anvilHighlight) m.firstSocket?.let { live(it.slot) } else null
		ctx.text(font, "§7Inventory", ix, iy - 10, MenuTheme.GREY)
		for (row in 0..3) for (col in 0..8) {
			val slot = WorkbenchClicks.inventorySlot(row, col, m.chestSlots)
			val hl = left?.takeIf { !it.isEmpty }?.let { l -> if (AnvilReforgeHelpers.combinable(l, live(slot))) 0x6000FF00 else null }
			val tx = ix + col * 18; val ty = iy + row * 18 + (if (row == 3) 4 else 0)
			tile(ctx, mx, my, tx, ty, slot, real = true, overlay = hl)

			standInSlotCell(slot, tx + 1, ty + 1)
		}
		lastMouse = mx.toDouble() to my.toDouble()

		val rx = x + pad + colW + 6; val rw = x + w - pad - rx
		if (rw >= 60) {
			MenuTheme.panel(ctx, rx, ay, rw, y + h - pad - ay, p)
			var ry = ay + 5
			ctx.text(font, "§fInputs", rx + 5, ry, MenuTheme.WHITE); ry += 11
			for ((n, s) in m.sockets.sortedWith(compareBy({ it.row }, { it.col })).withIndex()) {
				val label = s.hint?.let { live(it).takeIf { st -> !st.isEmpty }?.hoverName?.string?.trim() }?.takeIf { it.isNotEmpty() } ?: "Input ${n + 1}"
				val st = live(s.slot)
				val value = if (st.isEmpty) "§8empty" else "§f" + (if (st.count > 1) "${st.count}x " else "") + st.hoverName.string
				ctx.text(font, MenuTheme.fit("§7$label", rw - 10, font::width), rx + 5, ry, MenuTheme.GREY); ry += 10
				ctx.text(font, MenuTheme.fit(value, rw - 14, font::width), rx + 9, ry, MenuTheme.WHITE); ry += 12
			}
			ry += 2
			for (b in m.buttons) {
				if (ry + 14 > y + h - pad - 4) break
				val st = live(b.slot)
				val name = st.takeIf { !it.isEmpty }?.hoverName?.string?.trim()?.ifEmpty { null } ?: b.name.ifEmpty { "Button" }
				button(ctx, mx, my, rx + 5, ry, rw - 10, 14, name) { btn -> click(b.slot, btn, b.role.realSlot) }
				ry += 16
				for (line in WorkbenchParser.costLines(b.lore).take(3)) {
					if (ry + 9 > y + h - pad - 4) break
					ctx.text(font, MenuTheme.fit("§e$line", rw - 12, font::width), rx + 7, ry, MenuTheme.GOLD); ry += 10
				}
				ry += 2
			}
		}

		val carried = screen.menu.carried
		if (!carried.isEmpty) { ctx.item(carried, mx - 8, my - 8); ctx.itemDecorations(font, carried, mx - 8, my - 8) }
		else tooltip?.let { ctx.setTooltipForNextFrame(font, it, mx, my) }
		tooltip = null
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		shift = click.hasShiftDown()

		return super.mouseClicked(click, doubled)
	}
}
