// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.skills

import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker

object SkillOverlay {
	@Config
	object TConfig : ManagedConfig("skill-overlay", Category.MISC) {
		val enabled by toggle("enabled") { false }
		val hideAfterSeconds by integer("hide-after", 5, 300) { 60 }
		val hud by position("hud", 140, 40) { Vector2i(5, 100) }
		val showEta by toggle("show-eta") { true }

		val xpBar by toggle("xp-bar") { true }
		val xpBarSeconds by integer("xp-bar-seconds", 1, 30) { 4 }

		val xpBarDebugLog by toggle("xp-bar-debug-log") { false }
	}

	private val xpRegex = "\\+([0-9,.]+) ([A-Za-z]+) \\(([^)]+)\\)".toRegex()

	class SkillSession(val name: String) {
		var lastGain = 0.0
		var progress = ""
		var lastUpdate = TimeMark.now()
		val tracker = SessionTracker(idleTimeout = 30.seconds, resetAfter = null)
	}

	val sessions = mutableMapOf<String, SkillSession>()
	var current: SkillSession? = null

	fun parseGain(actionBar: String): Triple<Double, String, String>? {
		val m = xpRegex.find(actionBar) ?: return null
		val gain = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
		return Triple(gain, m.groupValues[2], m.groupValues[3])
	}

	fun remainingXp(progress: String): Double? {
		val parts = progress.split('/')
		if (parts.size != 2) return null
		val cur = parseShort(parts[0]) ?: return null
		val max = parseShort(parts[1]) ?: return null
		return (max - cur).coerceAtLeast(0.0)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (TConfig.xpBar) SkillXpBar.onChat(event.bareText)
	}

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!TConfig.enabled && !TConfig.xpBar) return
		val (gain, skill, progress) = parseGain(event.bareText) ?: return
		if (TConfig.xpBar) SkillXpBar.onGain(skill, progress, gain)
		if (!TConfig.enabled) return
		val session = sessions.getOrPut(skill) { SkillSession(skill) }
		val sinceLast = session.lastUpdate.passedTime()

		if (session.progress == progress && sinceLast < 3.seconds) return
		session.lastGain = gain
		session.tracker.add("XP", gain)
		session.progress = progress
		session.lastUpdate = TimeMark.now()
		current = session
	}

	fun parseShort(s: String): Double? {
		val t = s.replace(",", "").trim()
		val mult = when (t.lastOrNull()?.lowercaseChar()) { 'k' -> 1e3; 'm' -> 1e6; else -> 1.0 }
		return (if (mult != 1.0) t.dropLast(1) else t).toDoubleOrNull()?.times(mult)
	}

	private fun eta(session: SkillSession): String? {
		val left = remainingXp(session.progress) ?: return null
		val rate = session.tracker.perHour("XP")?.takeIf { it > 0 } ?: return null
		return ApexFormatters.timespanText((left / rate * 3600).toLong().seconds)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled) return
		val session = current ?: return
		if (session.lastUpdate.passedTime() > TConfig.hideAfterSeconds.seconds) return
		val lines = mutableListOf<Component>(
			Component.literal("§b${session.name} §7(${session.progress})"),
			Component.literal("§7Last: §a+${ApexFormatters.withThousands(session.lastGain, 1)}"),
			Component.literal("§7Session: §a+${ApexFormatters.withThousands(session.tracker.get("XP"), 1)}"),
		)
		session.tracker.perHour("XP")?.let { lines.add(Component.literal("§7XP/h: §e${ApexFormatters.withThousands(it.toLong())}")) }
		if (TConfig.showEta) eta(session)?.let { lines.add(Component.literal("§7Next level in: §f$it")) }
		event.drawHudLines(TConfig.hud, lines)
	}
}
