// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.skills

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.apexbzmenu.BzMenuLayout

object SkillLevelTips {
	const val NOT_LOADED = "§7Rewards not loaded yet"
	const val REPO_NOTE = "§8(from community data repo data)"
	const val MIN_W = 150
	const val MAX_W = 320

	var repo: (label: String, level: Int) -> List<String>? = { _, _ -> null }

	fun levelLines(label: String, level: Int, cached: SkillLevel?, repo: (String, Int) -> List<String>? = this.repo): List<String> {
		val title = "§f$label Level $level"
		if (cached != null && cached.lore.isNotEmpty()) return listOf(cached.name.ifEmpty { title }) + cached.lore
		val r = repo(label, level)?.takeIf { it.isNotEmpty() }
		if (r != null) return listOf(title) + r + REPO_NOTE
		return listOf(title, NOT_LOADED)
	}

	fun itemLines(name: String, lore: List<String>?): List<String> =
		listOf(name) + (lore?.takeIf { it.isNotEmpty() } ?: listOf(NOT_LOADED))

	fun wrap(lines: List<String>, maxWidth: Int, width: (String) -> Int): List<String> =
		lines.flatMap { BzMenuLayout.wrapText(it, maxWidth, width) }

	fun wrapWidth(screenW: Int) = (screenW / 2).coerceIn(MIN_W, MAX_W)

	fun show(ctx: GuiGraphicsExtractor, font: Font, lines: List<String>, mx: Int, my: Int, screenW: Int) {
		if (lines.isEmpty()) return
		ctx.setComponentTooltipForNextFrame(font, wrap(lines, wrapWidth(screenW), font::width).map { Component.literal(it) }, mx, my)
	}
}
