// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.features.menus.bank.BankParser
import dev.ashtonite365.apex.util.ScoreboardUtil

object PurseReadout {
	data class Purse(val label: String, val coins: Double)

	fun parse(sidebar: List<String>): Purse? = BankParser.sidebarPurseLine(sidebar)?.let { (l, c) -> Purse(l, c) }

	fun live(): Purse? = runCatching { parse(ScoreboardUtil.simplifiedScoreboardLines) }.getOrNull()

	fun amount(v: Double): String = java.text.DecimalFormat("#,##0.#", java.text.DecimalFormatSymbols(java.util.Locale.ROOT)).format(v)

	fun text(p: Purse) = "§7${p.label}: §6${amount(p.coins)} coins"

	fun lines(p: Purse?, maxWidth: Int, width: (String) -> Int): List<String> =
		p?.let { BzMenuLayout.wrapText(text(it), maxWidth, width) } ?: emptyList()

	const val LINE_H = 10

	fun draw(ctx: GuiGraphicsExtractor, font: Font, x: Int, y: Int, maxWidth: Int): Int {
		val l = lines(live(), maxWidth, font::width)
		l.forEachIndexed { i, s -> ctx.text(font, s, x, y + i * LINE_H, MenuTheme.GOLD) }
		return l.size * LINE_H
	}
}
