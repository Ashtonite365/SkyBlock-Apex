// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.support

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout
import dev.ashtonite365.apex.features.menus.MenuTheme
import dev.ashtonite365.apex.util.ScoreboardUtil

object CopperReadout {
	private val LINE = Regex("^\\s*(?:Your )?Copper:\\s*([\\d,]+(?:\\.\\d+)?)(?:\\s+Copper)?\\s*$", RegexOption.IGNORE_CASE)
	private val YOU_HAVE = Regex("^\\s*You have:?\\s*([\\d,]+(?:\\.\\d+)?) Copper\\s*$", RegexOption.IGNORE_CASE)

	private fun line(l: String): Double? {
		val c = MenuText.clean(l)
		return (LINE.matchEntire(c) ?: YOU_HAVE.matchEntire(c))?.groupValues?.get(1)?.let(MenuText::number)
	}

	fun parse(slots: List<SlotText>, sidebar: List<String>, tab: List<String> = emptyList()): Double? =
		slots.asSequence().flatMap { it.lore.asSequence() }.firstNotNullOfOrNull(::line)
			?: sidebar.firstNotNullOfOrNull(::line) ?: tab.firstNotNullOfOrNull(::line)

	fun text(copper: Double) = "§7Copper: §c" + PurseReadout.amount(copper)

	fun lines(copper: Double?, maxWidth: Int, width: (String) -> Int): List<String> =
		copper?.let { BzMenuLayout.wrapText(text(it), maxWidth, width) } ?: emptyList()

	private var loggedMissing = false

	fun draw(ctx: GuiGraphicsExtractor, font: Font, slots: List<SlotText>, x: Int, y: Int, maxWidth: Int): Int {
		val copper = runCatching { parse(slots, ScoreboardUtil.simplifiedScoreboardLines, tabLines()) }.getOrNull()
		if (copper == null) {
			if (!loggedMissing) { loggedMissing = true; Apex.logger.warn("[ApexMenus] SkyMart: no live copper line in menu lore, sidebar or tab list; copper readout hidden") }
		} else loggedMissing = false
		val l = lines(copper, maxWidth, font::width)
		l.forEachIndexed { i, s -> ctx.text(font, s, x, y + i * PurseReadout.LINE_H, MenuTheme.GOLD) }
		return l.size * PurseReadout.LINE_H
	}

	private fun tabLines(): List<String> = runCatching {
		dev.ashtonite365.apex.util.Client.game.connection?.listedOnlinePlayers?.mapNotNull { it.tabListDisplayName?.string }.orEmpty()
	}.getOrDefault(emptyList())
}
