/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (bazaar-1031): the Apex Bazaar. Clean-room implementation (no code from any other mod).
 *
 * A different DRAWING of Hypixel's real Bazaar chest and nothing else. It is hosted by the custom menus framework
 * (MenuRegistry): Hypixel's chest stays open underneath and every page change is Hypixel's own. Each Apex button is
 * exactly one click on the real slot it shows, re-checked by item name at the moment of the click ([BzPages]).
 * Pages it cannot read fall back to the generic styled wrapper, then vanilla. It never sends a command, never types
 * into a sign, never chains clicks and never reopens anything. Picking "Vanilla" in the style dropdown (or this
 * menu's Normal mode / the overlay switch) simply shows Hypixel's normal chest: the registry stops attaching it.
 * Open / close animations are the framework's (world <-> menu only).
 */
package dev.ashtonite365.apex.features.apexbzmenu

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.style.MenuStyle
import dev.ashtonite365.apex.features.menus.style.Style
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.repo.HypixelStaticData
import net.minecraft.world.entity.EquipmentSlot
import dev.ashtonite365.apex.util.bazaarStockOf
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object BzMenu {
	@Config
	object TConfig : ManagedConfig("bz-menu", Category.AUCTION_BAZAAR) {
		val overlay by toggle("overlay") { true }

		val packetDebug by toggle("packet-debug") { true }

		override fun migrate(saved: kotlinx.serialization.json.JsonObject) = dropRetired(saved)
	}

	val RETIRED_KEYS = setOf("in-apex-orders", "auto-chain", "show-on-open", "reveal-after-navigate", "toggle-key",
		"default-sort", "dim-low-liquidity")

	fun dropRetired(saved: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject =
		if (RETIRED_KEYS.none { it in saved }) saved else kotlinx.serialization.json.JsonObject(saved.filterKeys { it !in RETIRED_KEYS })

	private val bazaarPageTitle = Regex("^Bazaar[\\s\\u00A0\\u2000-\\u200B\\u202F]*[^\\p{L}\\p{N}\\s][\\s\\u00A0\\u2000-\\u200B\\u202F]*\\S")

	fun isBazaarPage(title: String) = bazaarPageTitle.containsMatchIn(title.replace(dev.ashtonite365.apex.util.SharedRegex.SECTION, "").trim())

	fun switchOn(): Boolean = runCatching { TConfig.overlay && BazaarMenuSettings.apex }.getOrDefault(false)

	@Config
	object BazaarMenuSettings : MenuSettings("bazaar") {

		override fun onReset() {
			@Suppress("UNCHECKED_CAST")
			(TConfig.allOptions["overlay"] as? ManagedOption<Boolean>)?.set(true)
			TConfig.markDirty()
		}
	}

	object BazaarMenu : CustomMenu {
		override val id = "bazaar"
		override val displayName = "Bazaar"
		override val title = Regex("^(?:Bazaar\\s*\\S|Your Bazaar Orders$|Order options$|Confirm Instant Buy$|How much do you want to pay\\?$|.+\\s\\S\\s*Instant(?: Buy| Sell)?$)")
		override val settings get() = BazaarMenuSettings

		override val enabled get() = switchOn()

		override fun create(screen: AbstractContainerScreen<*>): MenuView? =
			if (BzPages.kindOf(screen.title.string) != null || BzPages.arrowTitle(screen.title.string)) BzPageView(screen) else null

		override val slotPredicate: ((String, List<SlotText>) -> Boolean) = { t, slots ->
			BzPages.kindOf(t) == null && BzPages.kindOf(t, slots) != null
		}
	}

	@Subscribe
	fun onInit(event: ClientInitEvent) { MenuRegistry.register(BazaarMenu) }
}

internal fun bzLook(): BzLook = if (runCatching { MenuStyle.palette.style }.getOrNull() == Style.FORGE) BzLook.FORGE else BzLook.SKYGLASS

class BzPageView(screen: AbstractContainerScreen<*>) : ApexViewBase<BzPage>(screen) {
	private var scroll = 0
	private var contentHeight = 0
	private var contentH = 0

	override fun parse(title: String, slots: List<SlotText>): BzPage? =
		BzPages.parse(title, slots, screen.menu.slots.size - 36)

	override fun palette() = MenuStyle.palette.theme()

	init { inventoryTabOn = openState(inventoryTabOn, lastSeenMs, System.currentTimeMillis()) }

	override fun draw(ctx: GuiGraphicsExtractor, m: BzPage, mx: Int, my: Int) {
		lastSeenMs = System.currentTimeMillis()
		val sp = MenuStyle.palette
		val look = bzLook()
		ctx.text(font, BzPageLayout.fit(m.title, w - 2 * BzPageLayout.PAD - 4, font::width), x + BzPageLayout.PAD + 2, y + BzPageLayout.PAD + 4, sp.accent, false)
		pageKind = m.kind
		val inv = if (m.kind == BzPageKind.CATEGORY) inventoryTabOn else null
		val r = BzPageLayout.place(m, look, x, y, w, h, scroll, font::width, inv)
		scroll = scroll.coerceIn(0, maxOf(0, r.contentHeight - r.content[3]))
		contentHeight = r.contentHeight; contentH = r.content[3]
		var tip: net.minecraft.world.item.ItemStack? = null
		val fixed = r.placed.filter { !it.scrolled }
		val view = r.view
		if (view != null && r.placed.any { it.scrolled }) {

			beginClip(ctx, dev.ashtonite365.apex.features.menus.MenuBounds(view[0], view[1], view[2], view[3]))
			try { for (p in r.placed) if (p.scrolled) drawButton(ctx, p, mx, my, look) } finally { endClip(ctx) }
			drawScrollBar(ctx, view, r.contentHeight, scroll)
		}

		r.placed.firstOrNull { BzPageView.isProductItem(m.kind, it.button) && hovered(mx, my, it.x, it.y, it.w, it.h) }
			?.let { tip = stacks[it.button.slot]?.takeIf { st -> !st.isEmpty } }
		for (p in fixed) {
			drawButton(ctx, p, mx, my, look)

			if ((p.button.role == BzRole.NAV || p.button.role == BzRole.FOOT) && hovered(mx, my, p.x, p.y, p.w, p.h))
				tip = stacks[p.button.slot]?.takeIf { !it.isEmpty }
		}
		r.list?.let { tip = drawHeld(ctx, it, mx, my, look) ?: tip }
		if (screen.menu.carried.isEmpty) tip?.let { ctx.setTooltipForNextFrame(font, it, mx, my) }
	}

	private fun heldStacks(): List<Pair<net.minecraft.world.item.ItemStack, Int?>> {
		val n = screen.menu.slots.size
		val main = BzHeld.playerSlots(n).map { screen.menu.slots[it].item to (it as Int?) }
		val extra = runCatching {
			val pl = net.minecraft.client.Minecraft.getInstance().player
			listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND).mapNotNull { pl?.getItemBySlot(it) }
		}.getOrDefault(emptyList()).map { it to (null as Int?) }
		return (main + extra).filter { !it.first.isEmpty }
	}

	private var shift = false

	override fun mouseClicked(click: net.minecraft.client.input.MouseButtonEvent, doubled: Boolean): Boolean {
		shift = click.hasShiftDown()
		return super.mouseClicked(click, doubled)
	}

	private fun clickHeld(row: BzHeld.Item, button: Int) {
		if (!windowStillOpen()) return
		val menu = screen.menu
		val live = row.menuSlot?.let { menu.slots.getOrNull(it) }
		val liveKey = live?.item?.takeIf { !it.isEmpty }?.let { runCatching { it.view().repoItemId?.toString() }.getOrNull() }
		BzHeld.sendRowClick(menu, row, liveKey, button, shift)
	}

	private fun bazaarItemOf(st: net.minecraft.world.item.ItemStack, slot: Int): BzHeld.Item? {
		val id = runCatching { st.view().repoItemId }.getOrNull() ?: return null
		val product = runCatching { HypixelStaticData.bazaarData[bazaarStockOf(id)] }.getOrNull()
		return BzHeld.bazaarItem(id.toString(), MenuText.clean(st.hoverName.string), st.count, product != null,
			product?.quickStatus?.sellPrice, slot)
	}

	private fun drawHeld(ctx: GuiGraphicsExtractor, r: IntArray, mx: Int, my: Int, look: BzLook): net.minecraft.world.item.ItemStack? {
		val sp = MenuStyle.palette
		val (lx, ly, lw) = listOf(r[0], r[1], r[2])
		val stacks = heldStacks()
		val rows = BzHeld.slotRows(stacks.mapIndexed { i, (st, ws) -> bazaarItemOf(st, i)?.copy(menuSlot = ws) })
		contentHeight = BzHeld.listHeight(rows.size); contentH = maxOf(0, r[3] - BzHeld.HEADER_H)
		scroll = scroll.coerceIn(0, maxOf(0, contentHeight - contentH))
		val rw = if (contentHeight > contentH) lw - BzScroll.RESERVE else lw
		val each = lx + rw - 4 - BzHeld.TOTAL_W - 4 - BzHeld.EACH_W
		val tot = lx + rw - 4 - BzHeld.TOTAL_W
		ctx.text(font, BzPageLayout.fit("Bazaar items: ${rows.size} · Total ≈ ${BzHeld.coins(BzHeld.grandTotal(rows))} coins", lw - 4, font::width), lx + 2, ly + 2, sp.accent, false)
		ctx.text(font, "Item", lx + 21, ly + 14, sp.textDim, false)
		ctx.text(font, "Each", each, ly + 14, sp.textDim, false)
		ctx.text(font, "Total", tot, ly + 14, sp.textDim, false)
		if (rows.isEmpty()) ctx.text(font, BzHeld.EMPTY_TEXT, lx + 21, ly + BzHeld.HEADER_H + 6, sp.textDim, false)
		var tip: net.minecraft.world.item.ItemStack? = null
		val body = BzHeld.body(r)
		beginClip(ctx, dev.ashtonite365.apex.features.menus.MenuBounds(body[0], body[1], body[2], body[3]))
		try { for ((i, rr) in BzHeld.rowRects(r, rows.size, scroll)) {
			val row = rows[i]
			val hover = hovered(mx, my, rr[0], rr[1], rr[2], rr[3])
			if (look == BzLook.SKYGLASS) dev.ashtonite365.apex.features.menus.style.GuiCanvas(ctx, font).frost(rr[0], rr[1], rr[2], rr[3], sp.controlRadius, 0.6)
			MenuTheme.button(ctx, font, rr[0], rr[1], rr[2], rr[3], "", MenuTheme.state(hover, false), palette = palette())
			if (row.menuSlot != null) area(rr[0], rr[1], rr[2], rr[3]) { b -> clickHeld(row, b) }
			stacks.getOrNull(row.slot)?.first?.let { ctx.item(it, rr[0] + 2, rr[1] + 2); if (hover) tip = it }
			val ty = rr[1] + 6
			val name = "${row.name} ${BzHeld.countText(row)}"
			ctx.text(font, BzPageLayout.fit(name, each - 4 - (lx + 21), font::width), lx + 21, ty, sp.text, false)
			ctx.text(font, BzPageLayout.fit(BzHeld.unitText(row), BzHeld.EACH_W, font::width), each, ty, BzColor.mix(sp.textDim, sp.text, 0.45f), false)
			ctx.text(font, BzPageLayout.fit(BzHeld.totalText(row), BzHeld.TOTAL_W, font::width), tot, ty, if (row.unit == null) sp.textDim else sp.accent, false)
		} } finally { endClip(ctx) }
		drawScrollBar(ctx, body, contentHeight, scroll)
		return tip
	}

	private var pageKind: BzPageKind? = null

	private fun quoteOf(b: BzButton): BzPrices.Quote? {
		val q = runCatching {
			stacks[b.slot]?.view()?.repoItemId?.let { HypixelStaticData.bazaarData[bazaarStockOf(it)]?.quickStatus }
		}.getOrNull()
		return BzPrices.of(b.lore, q?.buyPrice, q?.sellPrice)
	}

	private fun drawScrollBar(ctx: GuiGraphicsExtractor, view: IntArray, total: Int, sc: Int) {
		val (track, knob) = BzScroll.bar(view, total, sc) ?: return
		val sp = MenuStyle.palette
		ctx.fill(track[0], track[1], track[0] + track[2], track[1] + track[3], (sp.textDim and 0x00FFFFFF) or 0x40000000)
		ctx.fill(knob[0], knob[1], knob[0] + knob[2], knob[1] + knob[3], sp.accent)
	}

	private fun drawButton(ctx: GuiGraphicsExtractor, p: BzPlaced, mx: Int, my: Int, look: BzLook) {
		val sp = MenuStyle.palette
		val b = p.button
		val hover = hovered(mx, my, p.x, p.y, p.w, p.h)

		if (look == BzLook.SKYGLASS) dev.ashtonite365.apex.features.menus.style.GuiCanvas(ctx, font).frost(p.x, p.y, p.w, p.h, sp.controlRadius, 0.6)
		MenuTheme.button(ctx, font, p.x, p.y, p.w, p.h, "", MenuTheme.state(hover, b.active), palette = palette())
		var tx = p.x + 4
		if (p.icon) {
			val st = if (b.role == BzRole.INV_TAB) INV_ICON else stacks[b.slot]
			st?.takeIf { !it.isEmpty }?.let { ctx.item(it, p.x + 2, p.y + (minOf(p.h, 20) - 16) / 2) }
			tx = p.x + 20
		}
		val tw = p.x + p.w - 4 - tx
		val multi = b.role == BzRole.TILE || b.role == BzRole.OPTION || b.role == BzRole.ORDER
		val value = BzPages.sellValue(b)?.first
		if (!multi) {
			val label = BzPageLayout.fit(b.name, tw, font::width)
			val lx = if (p.icon) tx else p.x + (p.w - font.width(label)) / 2
			if (value != null && p.h >= 26) {

				ctx.text(font, label, lx, p.y + 4, sp.text, false)
				ctx.text(font, BzPageLayout.fit(value, tw, font::width), tx, p.y + 16, sp.accent, false)
			} else ctx.text(font, label, lx, p.y + (p.h - 8) / 2, sp.text, false)
		} else {
			val action = BzPageView.actionLine(b)
			val t = BzPageLayout.cardText(p, action != null)
			ctx.text(font, BzPageLayout.fit(b.name, p.x + p.w - 4 - t.nameX, font::width), t.nameX, t.nameY, sp.text, false)

			val detail = BzColor.mix(sp.textDim, sp.text, 0.45f)
			var ly = t.detailY
			val quote = if (b.role == BzRole.TILE && pageKind == BzPageKind.PRODUCT_GROUP) quoteOf(b) else null
			if (quote != null) for ((label, v) in BzPrices.lines(quote)) {
				if (ly + 9 > t.detailBottom) break

				val right = p.x + p.w - 4
				val vw = font.width(v)
				ctx.text(font, BzPageLayout.fit(label, right - vw - 4 - t.detailX, font::width), t.detailX, ly, detail, false)
				ctx.text(font, v, right - vw, ly, sp.accent, false)
				ly += t.step
			}
			for (l in if (quote != null) BzPrices.otherLines(BzPageView.detailLines(b)) else BzPageView.detailLines(b)) {
				if (ly + 9 > t.detailBottom) break
				ctx.text(font, BzPageLayout.fit(l, p.x + p.w - 4 - t.detailX, font::width), t.detailX, ly, detail, false)
				ly += t.step
			}

			if (action != null && t.actionY != null) ctx.text(font, BzPageLayout.fit(action, p.w - 8, font::width), p.x + 4, t.actionY, sp.accent, false)
		}
		if (b.role == BzRole.INV_TAB) area(p.x, p.y, p.w, p.h) { inventoryTabOn = true; scroll = 0 }
		else area(p.x, p.y, p.w, p.h) { button ->

			if (leavesInventory(b)) { inventoryTabOn = false; scroll = 0 }
			click(b, button)
		}
	}

	private fun click(b: BzButton, button: Int) {
		val live = screen.menu.slots.getOrNull(b.slot)?.item?.takeIf { !it.isEmpty }?.hoverName?.string
		val slot = BzPages.clickTarget(b, live) ?: run {
			dev.ashtonite365.apex.Apex.logger.info("[ApexBz] '${b.name}' moved or changed (slot ${b.slot} shows '${live ?: "nothing"}'); nothing clicked")
			return
		}
		clickSlot(slot, button)
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		scroll = (scroll - (amount * 20).toInt()).coerceIn(0, maxOf(0, contentHeight - contentH))
		return true
	}

	companion object {

		var inventoryTabOn = true

		private var lastSeenMs = 0L
		const val REOPEN_MS = 1500L

		fun openState(current: Boolean, lastSeen: Long, now: Long): Boolean = if (now - lastSeen > REOPEN_MS) true else current

		fun leavesInventory(b: BzButton) = b.role == BzRole.CATEGORY || (b.role == BzRole.NAV && b.name.contains("Search", true))
		private val INV_ICON by lazy { net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST) }

		fun isProductItem(kind: BzPageKind, b: BzButton) = kind == BzPageKind.PRODUCT && b.role == BzRole.OPTION && b.name !in BzPages.PRODUCT_ACTIONS

		fun detailLines(b: BzButton): List<String> = b.lore.filter { it.isNotBlank() && !it.startsWith("Click to") }

		fun actionLine(b: BzButton): String? = b.lore.lastOrNull { it.startsWith("Click to") }
	}
}
