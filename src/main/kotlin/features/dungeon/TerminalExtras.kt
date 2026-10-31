// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ModifyChatEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

class TerminalTracker {
	data class Completion(val name: String, val action: String, val type: String, val current: Int, val total: Int)

	var section = 1; private set
	var terminals = 0; private set
	var levers = 0; private set
	var device = false; private set
	var gate = false; private set
	var active = false; private set
	var phaseStart = 0L; private set
	var sectionStart = 0L; private set
	val sectionTimes = mutableListOf<Long>()

	private var objectivesDone = false

	val termsNeeded get() = if (section == 2) 5 else 4

	fun onChat(line: String, now: Long): Completion? {
		if (goldorRegex.matches(line)) {
			reset()
			active = true
			phaseStart = now
			sectionStart = now
			return null
		}
		if (!active) return null
		if (gateRegex.matches(line)) {
			if (objectivesDone) advance(now) else gate = true
			return null
		}
		if (coreRegex.matches(line)) {
			sectionTimes += now - sectionStart
			active = false
			return null
		}
		val completion = parse(line) ?: return null
		if (completion.current == completion.total) {
			if (gate) advance(now) else objectivesDone = true
		} else {
			count(completion.type)
		}
		return completion
	}

	private fun parse(line: String): Completion? {
		val g = completedRegex.matchEntire(line)?.groupValues ?: return null
		return Completion(g[1], g[2], g[3], g[4].toInt(), g[5].toInt())
	}

	private fun count(type: String) {
		if (type == "terminal") terminals++
		else if (type == "lever") levers++
		else if (type == "device") device = true
	}

	private fun clearSection() {
		terminals = 0
		levers = 0
		device = false
		gate = false
		objectivesDone = false
	}

	private fun advance(now: Long) {
		sectionTimes += now - sectionStart
		sectionStart = now
		section += 1
		clearSection()
	}

	fun reset() {
		section = 1
		clearSection()
		active = false
		sectionTimes.clear()
	}

	companion object {
		val completedRegex = Regex("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$")
		val goldorRegex = Regex("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$")
		val coreRegex = Regex("^The Core entrance is opening!$")
		val gateRegex = Regex("^The gate has been destroyed!$")
		private val ampCode = Regex("&([0-9a-fk-orA-FK-OR])")

		fun formatTitle(template: String, c: Completion): String {
			val fields = listOf("{name}" to c.name, "{action}" to c.action, "{type}" to c.type,
				"{current}" to c.current.toString(), "{total}" to c.total.toString())
			val filled = fields.fold(template) { acc, (k, v) -> acc.replace(k, v) }
			return filled.replace(ampCode, "\u00A7$1")
		}

		fun seconds(ms: Long) = String.format("%.2f", ms / 1000.0)
	}
}

object TerminalExtras {
	val identifier: String
		get() = "terminal-extras"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val splits by toggle("splits") { true }
		val statusHud by toggle("status-hud") { true }
		val hud by position("hud", 80, 40) { Vector2i(4, 120) }
		val titles by toggle("titles") { false }
		val titleSelfOnly by toggle("title-self-only") { false }
		val titleFormat by string("title-format") { "&6{name} &8[&c{current}&8/&a{total}&8]" }
		val sounds by toggle("sounds") { false }

		val waypoints by toggle("waypoints") { false }

		val terminalPbs by toggle("terminal-pbs") { false }
	}

	val tracker = TerminalTracker()

	private fun timesLine(): Component {
		val parts = tracker.sectionTimes.joinToString(" §8| ") { "§a${TerminalTracker.seconds(it)}s" }
		return Component.literal("§bTimes: $parts§8, §bTotal: §a${TerminalTracker.seconds(tracker.sectionTimes.sum())}s")
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		val sectionBefore = tracker.section
		val activeBefore = tracker.active
		val done = tracker.onChat(event.bareText.trim(), System.currentTimeMillis())
		val phaseEnded = activeBefore && !tracker.active
		if (TConfig.splits && phaseEnded && tracker.sectionTimes.isNotEmpty()) Client.showChat(timesLine())
		if (TConfig.sounds && (phaseEnded || sectionBefore != tracker.section))
			Client.player?.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f)
		if (done == null || !TConfig.titles) return
		if (TConfig.titleSelfOnly && done.name != Client.playerName) return
		val title = TerminalTracker.formatTitle(TConfig.titleFormat, done)
		if (title.isEmpty()) return
		Client.hud.setTitle(Component.empty())
		Client.hud.setSubtitle(Component.literal(title))
	}

	@Subscribe
	fun onModifyChat(event: ModifyChatEvent) {
		if (!TConfig.splits) return
		if (!tracker.active && tracker.sectionTimes.isEmpty()) return
		val g = TerminalTracker.completedRegex.matchEntire(event.bareText.trim())?.groupValues ?: return
		val now = System.currentTimeMillis()
		val sectionT = TerminalTracker.seconds(now - tracker.sectionStart)
		val phaseT = TerminalTracker.seconds(now - tracker.phaseStart)
		event.replaceWith = Component.literal("§6${g[1]} §a${g[2]} a ${g[3]}! (§c${g[4]}§a/${g[5]}) §8(§7${sectionT}s §8| §7${phaseT}s§8)")
	}

	private fun tick(ok: Boolean) = if (ok) "§a✔" else "§c✘"
	private fun progress(n: Int, of: Int) = (if (n == of) "§a" else "§c") + "$n§8/§a$of"

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.statusHud || !tracker.active) return
		val t = tracker
		val lines = listOf(
			"§6Levers ${progress(t.levers, 2)}",
			"§6Terms ${progress(t.terminals, t.termsNeeded)}",
			"§6Device ${tick(t.device)}",
			"§6Gate ${tick(t.gate)}",
		)
		event.drawHudLines(TConfig.hud, lines.map { Component.literal(it) })
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = tracker.reset()
}
