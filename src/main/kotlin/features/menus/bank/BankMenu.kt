// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.bank

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ClientInitEvent
import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuChainRunner
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuSettings
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.MenuView
import dev.ashtonite365.apex.features.menus.support.ApexViewBase
import dev.ashtonite365.apex.features.menus.support.MenuText
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.customgui.customGui
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.data.Config

@Config
object BankMenuSettings : MenuSettings("bank", panels = listOf("interest", "history")) {

	const val AUTO_DEFAULT_ON = true
	const val PRESETS_SHOW_WARNING = false
	const val ONE_PRESS_SHOW_WARNING = true
	val historySort by choice("history-sort") { TxSort.NEWEST }

	val presets by toggle("presets") { true }

}

object BankMenu : CustomMenu {
	override val id = "bank"
	override val displayName = "Bank"
	override val title = BankParser.ANY_TITLE
	override val settings get() = BankMenuSettings
	override val defaultSize = 340 to 250

	@Subscribe
	fun onInit(event: ClientInitEvent) {
		MenuRegistry.register(this)
	}

	@Subscribe
	fun onChat(event: dev.ashtonite365.apex.events.ProcessChatEvent) { if (enabled) BankMemory.onChat(event.bareText) }

	override fun create(screen: AbstractContainerScreen<*>): MenuView = BankView(screen)
}

object BankMemory {
	var main: BankModel? = null

	var deposit: BankModel? = null
	var withdraw: BankModel? = null

	var chatBalance: Double? = null
	fun remember(m: BankModel) {
		when (m.page) {
			BankPage.MAIN -> {
				val old = main

				if (old != null && (old.balance != m.balance || old.purse != m.purse)) { deposit = null; withdraw = null }
				if (m.balance != null) chatBalance = null
				main = m; BankProfileCache.remember(m)
			}
			BankPage.DEPOSIT -> deposit = m
			BankPage.WITHDRAW -> withdraw = m
		}
	}

	fun onChat(line: String) {
		val text = BankRefresh.chatBalanceText(line) ?: return
		deposit = null; withdraw = null
		chatBalance = if (BankRefresh.loreIsCurrent(main?.balance, text)) null else MenuText.number(text)
	}

	fun balance(m: BankModel): Double? = chatBalance ?: main?.balance ?: m.balance
	fun page(p: BankPage?) = when (p) { BankPage.DEPOSIT -> deposit; BankPage.WITHDRAW -> withdraw; else -> main }
	fun reset() { main = null; deposit = null; withdraw = null; chatBalance = null }
}
class BankView(screen: AbstractContainerScreen<*>) : ApexViewBase<BankModel>(screen), MenuView {
	private val c get() = BankMenuSettings.customisation

	private val historyScroll = dev.ashtonite365.apex.features.menus.support.ScrollView { scrollKey("history") }

	override fun palette() = c.palette
	override fun parse(title: String, slots: List<SlotText>) = BankParser.parse(title, slots)?.also { BankMemory.remember(it) }

	override fun draw(ctx: GuiGraphicsExtractor, m: BankModel, mx: Int, my: Int) {
		val p = palette()
		val pad = 6
		val main = if (m.page == BankPage.MAIN) m else BankMemory.main ?: m
		ctx.text(font, "§6§lBank" + (main.account?.let { " §7· $it" } ?: ""), x + pad, y + 7, MenuTheme.WHITE)
		val vLabel = "Vanilla"
		val vw = font.width(vLabel) + 10

		val purse = BankParser.livePurse(ScoreboardUtil.simplifiedScoreboardLines, m)
		val balance = BankMemory.balance(m)
		val cache = BankProfileCache.cached()
		val cap = main.cap ?: cache?.cap
		val tier = main.tier ?: cache?.tier
		val heroY = y + 22; val heroH = 50
		MenuTheme.panel(ctx, x + pad, heroY, w - 2 * pad, heroH, p)
		val showInterest = c.shows("interest")
		val cols = if (showInterest) 4 else 3
		val colW = (w - 2 * pad) / cols
		fun stat(i: Int, label: String, value: String, colour: Int) {
			val cx = x + pad + i * colW + 6
			ctx.text(font, "§7$label", cx, heroY + 5, MenuTheme.GREY)
			ctx.text(font, MenuTheme.fit(value, colW - 8, font::width), cx, heroY + 16, colour)
		}
		stat(0, "Bank cap", cap?.let { "§f" + MenuText.coins(it) } ?: "§8—", MenuTheme.WHITE)
		stat(1, "Bank balance", balance?.let { "§6" + MenuText.coins(it) } ?: "§8—", MenuTheme.GOLD)
		stat(2, "Purse", purse?.let { "§e" + MenuText.coins(it) } ?: "§8—", MenuTheme.WHITE)
		var pctInline = true
		if (showInterest) {

			val timer = main.interestInSeconds?.let { "§b" + MenuText.formatDuration(it) } ?: "§8—"
			val projFull = main.projectedInterest?.let { "§a+" + MenuText.coins(it) + (main.projectedPct?.let { p -> " §8($p)" } ?: "") }
			val projShort = main.projectedInterest?.let { "§a+" + MenuText.coins(it) }
			val col = x + pad + 3 * colW + 6
			val right = x + w - pad - 4
			val purseEnd = x + pad + 2 * colW + 6 + maxOf(font.width("§7Purse"), font.width(purse?.let { "§e" + MenuText.coins(it) } ?: "§8—"))
			val lay = BankLayout.interestRow(col, right, purseEnd + 6, font.width("§7Interest in"), font.width(timer),
				projFull?.let { font.width(it) }, projShort?.let { font.width(it) })
			pctInline = lay.full
			ctx.text(font, "§7Interest in", lay.x, heroY + 5, MenuTheme.GREY)
			ctx.text(font, timer, lay.x, heroY + 16, MenuTheme.WHITE)
			(if (lay.full) projFull else projShort)?.let { ctx.text(font, it, lay.projX, heroY + 16, MenuTheme.WHITE) }
		}

		val barX = x + pad + 6; val barW = w - 2 * pad - 12; val barY = heroY + 28
		BankCap.fraction(balance, cap)?.let { f ->
			ctx.fill(barX, barY, barX + barW, barY + 3, 0xFF2A2A2A.toInt())
			ctx.fill(barX, barY, barX + (barW * f).toInt(), barY + 3, if (f >= 0.999) 0xFFFF5555.toInt() else 0xFFFFAA00.toInt())
		}
		if (showInterest) {
			val info = listOfNotNull(tier?.let { "§d$it" }, cap?.let { "§7" + BankCap.text(balance, cap) },
				main.lastInterest?.let { "§7last §a+" + MenuText.coins(it) },
				main.projectedPct?.takeIf { !pctInline }?.let { "§7projected §8$it" }).joinToString(" §8· ")
			ctx.text(font, MenuTheme.fit(info, barW, font::width), barX, heroY + 36, MenuTheme.GREY)
		} else if (cap != null) ctx.text(font, "§7" + BankCap.text(balance, cap), barX, heroY + 36, MenuTheme.GREY)

		val cardY = heroY + heroH + 6; val cardH = 58
		val cardW = (w - 2 * pad - 6) / 2
		card(ctx, mx, my, m, purse, balance, x + pad, cardY, cardW, cardH, deposit = true)
		card(ctx, mx, my, m, purse, balance, x + pad + cardW + 6, cardY, cardW, cardH, deposit = false)
		val inY = cardY + cardH - 18

		if (!c.shows("history")) return
		val ty = inY + 22
		val th = y + h - pad - ty
		if (th < 24) return
		MenuTheme.panel(ctx, x + pad, ty, w - 2 * pad, th, p)
		val txs = BankParser.sort(main.transactions, BankMenuSettings.historySort)
		val net = BankParser.net(txs)
		ctx.text(font, "§fRecent transactions", x + pad + 6, ty + 5, MenuTheme.WHITE)
		if (txs.isNotEmpty()) {
			val ns = (if (net >= 0) "§anet +" else "§cnet -") + MenuText.coins(kotlin.math.abs(net))
			ctx.text(font, ns, x + w - pad - 6 - font.width(ns), ty + 5, MenuTheme.WHITE)
		}
		val rowH = 11

		val view = dev.ashtonite365.apex.features.menus.MenuBounds(x + pad + 1, ty + 16, w - 2 * pad - 2, maxOf(0, th - 17))
		historyScroll.layout(view, if (txs.isEmpty()) 0 else 1 + txs.size * rowH - 2)
		if (txs.isEmpty()) ctx.text(font, "§8None listed", x + pad + 6, ty + 18, MenuTheme.DIM)
		beginClip(ctx, view)
		val first = maxOf(0, (historyScroll.offset - 1) / rowH)
		txs.drop(first).take(view.h / rowH + 2).forEachIndexed { j, t ->
			val ry = ty + 17 + (first + j) * rowH - historyScroll.offset
			val amt = (if (t.sign > 0) "§a+" else "§c-") + MenuText.coins(t.amount)
			ctx.text(font, amt, x + pad + 6, ry, MenuTheme.WHITE)
			val right = MenuTheme.fit("§7${t.ageText}" + (t.who?.let { " §8by §f$it" } ?: ""), w - 2 * pad - 70, font::width)
			ctx.text(font, right, x + pad + 64, ry, MenuTheme.GREY)
		}
		endClip(ctx)
	}

	private fun presetsFor(deposit: Boolean) = if (deposit) listOf(BankAction.DEPOSIT_ALL to "All", BankAction.DEPOSIT_HALF to "Half", BankAction.DEPOSIT_CUSTOM to "Custom")
		else listOf(BankAction.WITHDRAW_ALL to "All", BankAction.WITHDRAW_HALF to "Half", BankAction.WITHDRAW_20 to "20%", BankAction.WITHDRAW_CUSTOM to "Custom")

	private fun card(ctx: GuiGraphicsExtractor, mx: Int, my: Int, m: BankModel, purse: Double?, balance: Double?, cx: Int, cy: Int, cw: Int, ch: Int, deposit: Boolean) {
		val p = palette()
		MenuTheme.panel(ctx, cx, cy, cw, ch, p)
		val page = if (deposit) BankPage.DEPOSIT else BankPage.WITHDRAW
		ctx.text(font, if (deposit) "§a§lDeposit" else "§c§lWithdraw", cx + 6, cy + 5, MenuTheme.WHITE)
		val open = if (deposit) BankAction.OPEN_DEPOSIT else BankAction.OPEN_WITHDRAW
		when {
			m.page == BankPage.MAIN -> m.button(open)?.let { b -> button(ctx, mx, my, cx + cw - 54, cy + 3, 50, 13, "Open ›") { _ -> press(m, open) } }
			m.page == page -> m.button(BankAction.BACK)?.let { b -> button(ctx, mx, my, cx + cw - 54, cy + 3, 50, 13, "‹ Back") { _ -> press(m, BankAction.BACK) } }
		}

		val presets = if (m.page != page && !BankMenuSettings.presets) emptyList()
			else presetsFor(deposit).filter { (a, _) -> m.page != page || m.button(a) != null || a == BankAction.DEPOSIT_CUSTOM || a == BankAction.WITHDRAW_CUSTOM }
		val labels = presets.map { it.second }

		val rects = BankLayout.fitPills(cx + 6, cy + 22, cw - 12, labels, font::width)
			?: BankLayout.pills(cx + 6, cy + 22, cw - 12, presets.size)
		val samePage = BankMemory.page(page).takeIf { m.page != page } ?: m
		val empty = BankAmounts.emptyReason(page, purse, balance)
		presets.forEachIndexed { i, (a, _) ->
			val r = rects[i]
			val here = m.page == page && m.button(a) != null
			val enabled = (here || (m.page == BankPage.MAIN && m.button(open) != null)) && empty == null
			val hover = enabled && MenuTheme.inside(mx, my, r.x, r.y, r.w, r.h)
			MenuTheme.button(ctx, font, r.x, r.y, r.w, r.h, "", MenuTheme.state(hover, false, enabled), palette = p)
			val tw = font.width(labels[i])
			if (tw <= r.w - 4) ctx.text(font, labels[i], r.x + (r.w - tw) / 2, r.y + 4, MenuTheme.WHITE)
			else { val sc = (r.w - 4f) / tw; ctx.pose().pushMatrix(); ctx.pose().translate(r.x + 2f, r.y + 5f); ctx.pose().scale(sc, sc); ctx.text(font, labels[i], 0, 0, MenuTheme.WHITE); ctx.pose().popMatrix() }
			if (enabled) area(r.x, r.y, r.w, r.h) { _ -> press(m, a, purse = purse, balance = balance) }
			if (empty == null) BankAmounts.amountLine(a, BankAmounts.shown(a, samePage, purse, balance))?.let { line ->
				val ar = BankLayout.amountRect(r)
				val lw = font.width(line.text)
				val sc = BankAmounts.fitScale(lw, ar.w)
				if (sc >= 1f) ctx.text(font, line.text, ar.x + (ar.w - lw) / 2, ar.y, line.colour)
				else { ctx.pose().pushMatrix(); ctx.pose().translate(ar.x.toFloat(), ar.y + 1f); ctx.pose().scale(sc, sc); ctx.text(font, line.text, 0, 0, line.colour); ctx.pose().popMatrix() }
			}
		}
		if (empty != null) ctx.text(font, "§8$empty", cx + 6, cy + ch - 9, MenuTheme.DIM)
		else
		if (m.page == page) ctx.text(font, "§8Hypixel's ${if (deposit) "deposit" else "withdraw"} page", cx + 6, cy + ch - 9, MenuTheme.DIM)
	}

	private fun press(m: BankModel, a: BankAction, purse: Double? = null, balance: Double? = null) {
		if (BankAmounts.emptyReason(a.page, purse, balance) != null) return
		if (drawingCached || MenuChainRunner.active) return

		BankChains.press(m, a, purse = purse, balance = balance).single?.let { clickSlot(it) }
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
		historyScroll.scroll(amount)
		return true
	}
}
