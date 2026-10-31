/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: dungeon puzzle status HUD, read from the tab list's "Puzzles: (N)" section. Read-only, no network.
 * Hidden during the boss fight when configured: a "[BOSS] " chat line other than The Watcher (blood room, still the
 * clear phase) or an F7 BossPhaseEvent sets the boss flag, and a world change clears it.
 */
package dev.ashtonite365.apex.features.kit.puzzles

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.util.kit.BossPhaseEvent
import dev.ashtonite365.apex.util.mc.TabListReader
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component

object PuzzleHud {
	enum class Status(val icon: String, val colour: String) { DONE("✔", "§a"), FAILED("✖", "§c"), OPEN("✦", "§6") }
	data class Entry(val name: String, val status: Status, val player: String?)
	data class State(val count: Int, val entries: List<Entry>)

	private val HEADER = Regex("^Puzzles: \\((\\d+)\\)$")
	private val ENTRY = Regex("^ ?(\\w+(?: \\w+)*|\\?\\?\\?): \\[([✖✔✦])] ?(?:\\((\\w+)\\))?$")

	private fun statusOf(icon: String): Status = Status.entries.firstOrNull { it.icon == icon && it != Status.OPEN } ?: Status.OPEN

	private fun entryOf(line: String): Entry? {
		val m = ENTRY.find(line.trimEnd()) ?: return null
		val player = m.groupValues[3]
		return Entry(m.groupValues[1], statusOf(m.groupValues[2]), if (player.isEmpty()) null else player)
	}

	fun parse(lines: List<String>): State? {
		for ((i, line) in lines.withIndex()) {
			val header = HEADER.matchEntire(line.trim()) ?: continue
			val count = header.groupValues[1].toInt()
			val entries = ArrayList<Entry>()
			var j = i + 1
			var taken = 0
			while (j < lines.size && taken < count) {
				entryOf(lines[j])?.let { entries.add(it) }
				j++; taken++
			}
			return State(count, entries)
		}
		return null
	}

	fun render(state: State, names: Boolean): List<String> {
		val out = ArrayList<String>(state.entries.size + 1)
		out.add("§5Puzzles §8(§3${state.count}§8)")
		for (e in state.entries) {
			val who = if (names && e.player != null) " §7(§6${e.player}§7)" else ""
			out.add("§e${e.name}§8: [${e.status.colour}${e.status.icon}§8]$who")
		}
		return out
	}

	private var state: State? = null

	fun isBossLine(line: String): Boolean {
		if (!line.startsWith("[BOSS] ")) return false
		return !line.startsWith("[BOSS] The Watcher")
	}

	var inBoss = false
		private set

	@Subscribe
	fun onBossChat(event: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		if (isBossLine(event.bareText)) inBoss = true
	}

	@Subscribe
	fun onBossPhase(event: BossPhaseEvent) {
		if (event.phase.boss == dev.ashtonite365.apex.util.kit.BossPhase.Boss.F7) inBoss = true
	}

	@Subscribe
	fun onWorldReset(event: WorldReadyEvent) {
		inBoss = false
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		val active = PuzzleSolvers.TConfig.puzzleHud && DungeonUtil.isInActiveDungeon
		state = if (!active) null else parse(TabListReader.currentSnapshot.body.map { it.bareText })
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val current = state ?: return
		val cfg = PuzzleSolvers.TConfig
		if (!cfg.puzzleHud || current.count == 0) return
		if (cfg.puzzleHudHideInBoss && inBoss) return
		val lines = render(current, cfg.puzzleHudNames).map { Component.literal(it) }
		event.drawHudLines(cfg.puzzleHudPosition, lines)
	}
}
