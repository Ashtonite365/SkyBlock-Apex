package dev.ashtonite365.apex.util

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import java.util.Optional
import net.minecraft.client.gui.Hud
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

object ScoreboardUtil {

	var scoreboardLines: List<Component> = emptyList()

	var simplifiedScoreboardLines: List<String> = emptyList()

	private const val SIDEBAR_ROWS = 15

	@Subscribe
	fun onTick(event: TickEvent) {
		val rows = readSidebar()
		scoreboardLines = rows
		simplifiedScoreboardLines = rows.map { it.bareText }
	}

	private fun readSidebar(): List<Component> {
		val board = Client.game.level?.scoreboard ?: return emptyList()
		val objective = board.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
		val ordered = board.listPlayerScores(objective).filterNot { it.isHidden }.sortedWith(Hud.SCORE_DISPLAY_ORDER)
		val rows = ArrayList<Component>(minOf(ordered.size, SIDEBAR_ROWS))
		for (entry in ordered) {
			if (rows.size == SIDEBAR_ROWS) break
			rows += PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner), entry.ownerName())
		}
		return rows
	}
}

private val nonColourCode = Regex("§[^a-f0-9]")

fun Component.formattedString(): String {
	val legacy = StringBuilder()
	val collect = FormattedText.StyledContentConsumer<Unit> { style, text ->
		style.color?.toChatFormatting()?.let { legacy.append('§').append(it.code) }
		if (style.isUnderlined) legacy.append("§n")
		if (style.isBold) legacy.append("§l")
		legacy.append(text)
		Optional.empty()
	}
	visit(collect, Style.EMPTY)
	return nonColourCode.replace(legacy, "")
}
