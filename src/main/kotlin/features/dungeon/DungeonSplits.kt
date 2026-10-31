// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon

import kotlin.time.Duration.Companion.milliseconds
import org.joml.Vector2i
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.kit.ServerTicks
import dev.ashtonite365.apex.features.kit.splits.KitSplitDefs
import dev.ashtonite365.apex.features.kit.splits.RunSummary
import dev.ashtonite365.apex.features.kit.splits.SplitRun
import dev.ashtonite365.apex.features.plus.dungeon.PlusDungeonState
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.render.drawHudLines
import net.minecraft.network.chat.Component

object DungeonSplits {
	val identifier: String
		get() = "dungeon-splits"

	@Config
	object TConfig : ManagedConfig(category = Category.DUNGEONS, name = identifier) {
		val enabled by toggle("enabled") { true }
		val hud by position("hud", 130, 60) { Vector2i(4, 210) }
		val bossPhases by toggle("boss-phases") { false }
		val tickTime by toggle("tick-time") { false }
		val hidePending by toggle("hide-pending") { false }
		val fixedWidth by toggle("fixed-width") { false }
		val runSummary by toggle("run-summary") { false }
		val autoExtraStats by riskyToggle("auto-extra-stats", RiskLevel.GREY_AREA)
	}

	enum class Split(val label: String) { BLOOD_OPEN("Blood Open"), BLOOD_CLEAR("Blood Clear"), BOSS_ENTRY("Boss Entry"), RUN_END("Run End") }

	private class Mark(val millis: Long, val tick: Long)

	private class Run(val startMillis: Long, val startTick: Long) {
		val marks = LinkedHashMap<Split, Mark>()
		var boss: SplitRun? = null
	}

	private var run: Run? = null
	private val stats = RunSummary()

	private fun serverTick() = ServerTicks.totalTicks

	private fun splitFor(line: String): Split? = when {
		line == "The BLOOD DOOR has been opened!" -> Split.BLOOD_OPEN
		line.startsWith("[BOSS] The Watcher: You have proven yourself") -> Split.BLOOD_CLEAR
		line.startsWith("[BOSS] ") && !line.startsWith("[BOSS] The Watcher") -> Split.BOSS_ENTRY
		line.contains("> EXTRA STATS <") -> Split.RUN_END
		else -> null
	}

	private fun feedBoss(r: Run, line: String, now: Long) {
		if (r.boss == null && TConfig.bossPhases) r.boss = KitSplitDefs.bossRun(PlusDungeonState.floor)
		r.boss?.onMessage(line, now, serverTick())
	}

	private fun handleSummary(raw: String) {
		if (TConfig.autoExtraStats && RunSummary.extraStatsHeader.matches(raw)) Client.runCommand("showextrastats")
		if (!TConfig.runSummary || !stats.feed(raw)) return
		stats.take()?.forEach { Client.showChat(Component.literal(it)) }
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val raw = event.bareText
		val line = raw.trim()
		val now = System.currentTimeMillis()
		run?.let { feedBoss(it, line, now) }
		if (DungeonUtil.isInDungeonIsland) handleSummary(raw)
		if (line.startsWith("[NPC] Mort: Here, I found this map")) {
			run = Run(now, serverTick())
			return
		}
		val r = run ?: return
		val split = splitFor(line) ?: return
		if (split !in r.marks) r.marks[split] = Mark(now, serverTick())
	}

	fun layoutRows(rows: List<Pair<String, String>>, fixed: Boolean): List<Component> {
		if (!fixed) return rows.map { (label, value) -> Component.literal("$label $value") }
		val font = Client.font
		val column = rows.maxOf { (label, value) -> font.width(label) + font.width(value) } + 8
		val spaceWidth = maxOf(1, font.width(" "))
		return rows.map { (label, value) ->
			val pad = maxOf(1, (column - font.width(label) - font.width(value)) / spaceWidth)
			Component.literal(label + " ".repeat(pad) + value)
		}
	}

	private fun span(ms: Long) = ApexFormatters.timespanText(ms.milliseconds)

	private fun bossRows(r: Run, now: Long): List<Pair<String, String>> {
		val boss = r.boss ?: return emptyList()
		val out = ArrayList<Pair<String, String>>()
		for (row in boss.rows(now, serverTick()).dropLast(1)) {
			val pending = row.time == 0L
			if (pending && TConfig.hidePending) continue
			val text = if (pending) "§7--"
			else "§a${span(row.time)}" + (if (TConfig.tickTime) " §8(§7${SplitRun.formatTicks(row.ticks)}§8)" else "")
			out += " ${row.name}:" to text
		}
		return out
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || !DungeonUtil.isInDungeonIsland) return
		val r = run ?: return
		val now = System.currentTimeMillis()
		val rows = ArrayList<Pair<String, String>>()
		var lastMillis = r.startMillis
		var lastTick = r.startTick
		for (split in Split.entries) {
			val mark = r.marks[split]
			if (mark == null && TConfig.hidePending) continue
			val text = StringBuilder()
			if (mark == null) text.append("§7--")
			else text.append("§a${span(mark.millis - r.startMillis)} §7(+${span(mark.millis - lastMillis)})")
			if (mark != null && TConfig.tickTime) text.append(" §8(§7${SplitRun.formatTicks(mark.tick - lastTick)}§8)")
			rows += "§e${split.label}:" to text.toString()
			if (mark != null) { lastMillis = mark.millis; lastTick = mark.tick }
			if (split == Split.BOSS_ENTRY && TConfig.bossPhases) rows += bossRows(r, now)
		}
		val end = r.marks[Split.RUN_END]?.millis ?: now
		rows += "§eTotal:" to "§f${span(end - r.startMillis)}"
		event.drawHudLines(TConfig.hud, layoutRows(rows, TConfig.fixedWidth))
	}
}
