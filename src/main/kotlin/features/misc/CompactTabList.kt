// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.misc

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.util.StringRepresentable
import io.github.notenoughupdates.moulconfig.ChromaColour
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.hud.HudStyle
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.bareText

object CompactTabList {
	@Config
	object TConfig : ManagedConfig("compact-tab-list", Category.GENERAL) {
		val enabled by toggle("enabled") { false }
		val showHeaderFooter by toggle("show-header-footer") { false }
		val hidePlayers by toggle("hide-players") { false }
		val maxRows by integer("max-rows", 10, 40) { 22 }
		val background by choice("background") { TabBackground.HUD_STYLE }
		val backgroundColour by colour("background-colour") { ChromaColour.fromStaticRGB(0, 0, 0, 128) }

		val playerSort by choice("player-sort") { PlayerSort.DEFAULT }
		val reverseSort by toggle("reverse-sort") { false }
		val hideLevel by toggle("hide-level") { false }
		val hideEmblems by toggle("hide-emblems") { false }
	}

	enum class PlayerSort(private val id: String) : StringRepresentable {
		DEFAULT("default"), LEVEL("level"), NAME("name");
		override fun getSerializedName() = id
	}

	enum class TabBackground(private val id: String) : StringRepresentable {
		HUD_STYLE("hud-style"), CUSTOM("custom"), NONE("none");
		override fun getSerializedName() = id
	}

	private val PLAYER_ENTRY = Regex("^\\[(\\d+)] ([A-Za-z0-9_]{1,16})(.*)$")
	private val LEVEL_TAG = Regex("\\[\\d+] ?")
	private val LEVEL_PIECE = Regex("\\[?\\d*]? ?")
	private val PLAYERS_TITLE = Regex("^Players \\(\\d+\\)$")

	private fun entryOf(text: String): MatchResult? = PLAYER_ENTRY.matchEntire(text.trim())

	fun parsePlayer(line: String): Pair<Int, String>? {
		val (level, name) = entryOf(line)?.destructured ?: return null
		return level.toInt() to name
	}

	fun sortPlayers(lines: List<Component>, sort: PlayerSort, reverse: Boolean): List<Component> {
		if (!reverse && sort == PlayerSort.DEFAULT) return lines
		val nonPlayers = ArrayList<Component>()
		val players = ArrayList<Component>()
		for (line in lines) (if (parsePlayer(line.bareText) != null) players else nonPlayers).add(line)
		val ordered: List<Component> = when (sort) {
			PlayerSort.DEFAULT -> players
			PlayerSort.LEVEL -> players.sortedWith(compareByDescending { parsePlayer(it.bareText)!!.first })
			PlayerSort.NAME -> players.sortedWith(compareBy { parsePlayer(it.bareText)!!.second.lowercase() })
		}
		return nonPlayers + (if (reverse) ordered.asReversed() else ordered)
	}

	fun cleanPlayerText(text: String, hideLevel: Boolean, hideEmblems: Boolean): String {
		val (level, name, rest) = entryOf(text)?.destructured ?: return text
		val head = if (hideLevel) "" else "[$level] "
		val tail = if (hideEmblems) "" else rest
		return head + name + tail
	}

	private fun restyle(line: Component): Component {
		val hideLevel = TConfig.hideLevel
		val hideEmblems = TConfig.hideEmblems
		if (!hideLevel && !hideEmblems) return line
		val name = entryOf(line.bareText)?.groupValues?.get(2) ?: return line

		var nameDone = false
		return dev.ashtonite365.apex.features.plus.visualwords.PlusText.mapSegments(line) { seg, style ->
			val text = when {
				nameDone -> if (TConfig.hideEmblems) "" else seg
				name in seg -> {
					nameDone = true
					val lead = seg.substringBefore(name)
					val trail = seg.substringAfter(name)
					(if (TConfig.hideLevel) lead.replace(LEVEL_TAG, "") else lead) + name + (if (TConfig.hideEmblems) "" else trail)
				}
				else -> if (TConfig.hideLevel) seg.replace(LEVEL_PIECE, "") else seg
			}
			text to style
		}
	}

	@JvmStatic
	fun isActive() = TConfig.enabled && SkyBlockSession.inSkyBlock

	private fun isPlayersTitle(line: Component) = PLAYERS_TITLE.matches(line.bareText.trim())

	private fun playerSectionEnd(body: List<Component>, title: Int): Int {
		for (i in title + 1 until body.size) {
			val s = body[i].bareText.trim()
			if (!s.startsWith("[") && !s.endsWith(":") && body[i].style.isBold || s == "Info") return i
		}
		return body.size
	}

	fun columns(): List<List<Component>> {
		var body: List<Component> = TabListReader.currentSnapshot.body.filter { it.bareText.isNotBlank() }
		if (TConfig.hidePlayers) {
			val title = body.indexOfFirst(::isPlayersTitle)
			if (title >= 0) body = body.subList(0, title) + body.subList(playerSectionEnd(body, title), body.size)
		}
		val title = body.indexOfFirst(::isPlayersTitle)
		val touched = TConfig.playerSort != PlayerSort.DEFAULT || TConfig.reverseSort || TConfig.hideLevel || TConfig.hideEmblems
		if (title >= 0 && touched) {
			var stop = title + 1
			while (stop < body.size && parsePlayer(body[stop].bareText) != null) stop++
			val section = sortPlayers(body.subList(title + 1, stop), TConfig.playerSort, TConfig.reverseSort).map(::restyle)
			body = body.subList(0, title + 1) + section + body.subList(stop, body.size)
		}
		return body.chunked(TConfig.maxRows)
	}

	@JvmStatic
	fun render(ctx: GuiGraphicsExtractor) {
		val font = Client.font
		val cols = columns()
		if (cols.isEmpty()) return
		val tab = TabListReader.currentSnapshot
		val header = if (TConfig.showHeaderFooter) tab.header.map { font.split(it, 400) }.orElse(listOf()) else listOf()
		val footer = if (TConfig.showHeaderFooter) tab.footer.map { font.split(it, 400) }.orElse(listOf()) else listOf()
		val lineH = font.lineHeight + 1
		val colW = cols.map { col -> col.maxOf { font.width(it) } + 8 }
		val bodyW = colW.sum()
		val totalW = maxOf(bodyW, (header + footer).maxOfOrNull { font.width(it) } ?: 0)
		val rows = cols.maxOf { it.size }
		val totalH = (header.size + rows + footer.size) * lineH
		val left = ctx.guiWidth() / 2 - totalW / 2
		val top = 10
		when (TConfig.background) {
			TabBackground.HUD_STYLE -> HudStyle.drawBackground(ctx, left - 3, top - 3, left + totalW + 3, top + totalH + 2)
			TabBackground.CUSTOM -> ctx.fill(left - 3, top - 3, left + totalW + 3, top + totalH + 2, TConfig.backgroundColour.getEffectiveColourRGB())
			TabBackground.NONE -> {}
		}
		val colour = HudStyle.textColour()
		val shadow = HudStyle.TConfig.textShadow
		var y = top
		for (line in header) {
			ctx.text(font, line, left + totalW / 2 - font.width(line) / 2, y, colour, shadow)
			y += lineH
		}
		var x = left + (totalW - bodyW) / 2
		for ((ci, col) in cols.withIndex()) {
			for ((ri, line) in col.withIndex()) ctx.text(font, line, x + 4, y + ri * lineH, colour, shadow)
			x += colW[ci]
		}
		y += rows * lineH
		for (line in footer) {
			ctx.text(font, line, left + totalW / 2 - font.width(line) / 2, y, colour, shadow)
			y += lineH
		}
	}
}
