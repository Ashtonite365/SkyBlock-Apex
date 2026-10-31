/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * APEX-26.3 PORT: Jerry, Crimson Isle and Rift helpers. Original implementation written for this port
 * from feature descriptions only (similar features exist in and); no code copied.
 * Built on the shared Timers, SessionTracker, HudStyle (drawHudLines) and Alerts systems.
 */
package dev.ashtonite365.apex.features.islands

import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.commands.onRun
import dev.ashtonite365.apex.events.CommandEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.features.parsers.CrimsonParser
import dev.ashtonite365.apex.features.parsers.JerryParser
import dev.ashtonite365.apex.features.parsers.RiftParser
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tr
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText

object JerryHelper {
	@Config
	object TConfig : ManagedConfig("jerry-helper", Category.EVENTS) {
		val enabled by toggle("enabled") { true }
		val hud by toggle("hud") { false }
		val hudPosition by position("hud-position", 120, 50) { Vector2i(5, 300) }
	}

	@Serializable
	data class Store(val counts: MutableMap<String, Int> = mutableMapOf(), var lastJerryMillis: Long? = null)

	@Config
	object DConfig : ProfileSpecificDataHolder<Store>(serializer(), "jerry-helper", ::Store)

	private fun asParserData(s: Store) = JerryParser.JerryData(
		s.counts.mapKeys { JerryParser.Colour.valueOf(it.key) }.toMutableMap(), s.lastJerryMillis)

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled) return
		val store = DConfig.data ?: return
		val colour = JerryParser.parse(event.bareText) ?: return
		store.counts.merge(colour.name, 1, Int::plus)
		store.lastJerryMillis = System.currentTimeMillis()
		DConfig.markDirty()
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {
		if (!TConfig.enabled) return
		val store = DConfig.data ?: return
		val since = JerryParser.timeSinceLast(asParserData(store), System.currentTimeMillis()) ?: return
		event.add(Timers.TimerEntry("jerry", "Last Hidden Jerry", "§7${ApexFormatters.timespanText(since)} ago", false, null))
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || !TConfig.hud || !SkyBlockSession.inSkyBlock) return
		val store = DConfig.data ?: return
		if (store.counts.isEmpty()) return
		val colours = mapOf("GREEN" to "§a", "BLUE" to "§9", "PURPLE" to "§5", "GOLDEN" to "§6")
		val lines = listOf(Component.literal("§eHidden Jerries")) + JerryParser.Colour.entries.mapNotNull { c ->
			store.counts[c.name]?.let { Component.literal("${colours[c.name]}${c.name.lowercase().replaceFirstChar(Char::uppercase)}: §f$it") }
		}
		event.drawHudLines(TConfig.hudPosition, lines)
	}

	@Subscribe
	fun onCommand(event: CommandEvent.SubCommand) {
		event.addSubcommand("resetjerry", dev.ashtonite365.apex.util.data.ManagedConfig.Category.EVENTS) {
			onRun {
				DConfig.data?.let { it.counts.clear(); it.lastJerryMillis = null }
				DConfig.markDirty()
				source.sendFeedback(tr("apex.jerry.reset", "Reset the Hidden Jerry counter."))
			}
		}
	}
}

object CrimsonIsleHelper {
	@Config
	object TConfig : ManagedConfig("crimson-isle", Category.CRIMSON_ISLE) {
		val reputationTracker by toggle("reputation-tracker") { true }
		val reputationHud by position("reputation-hud", 120, 30) { Vector2i(5, 340) }
		val volcanoDisplay by toggle("volcano-display") { true }
		val volcanoHud by position("volcano-hud", 120, 10) { Vector2i(5, 380) }
		val pabloHelper by toggle("pablo-helper") { true }
		val sirihHelper by toggle("sirih-helper") { true }
		val sulphurSkitterBox by riskyToggle("sulphur-skitter-box", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
	}

	private val lastKill = mutableMapOf<CrimsonParser.Miniboss, TimeMark>()
	val reputation = SessionTracker(idleTimeout = 5.minutes, resetAfter = 30.minutes)

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText
		CrimsonParser.parseMinibossDown(line)?.let { lastKill[it] = TimeMark.now() }
		if (TConfig.reputationTracker) CrimsonParser.parseReputation(line)?.let {
			reputation.add(if (it.faction == CrimsonParser.Faction.MAGE) "Mage Reputation" else "Barbarian Reputation", it.amount.toDouble())
		}
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {

		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.CRIMSON_ISLE) return
		for ((boss, mark) in lastKill) {
			val label = boss.chatName.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
			event.add(Timers.TimerEntry("crimson-minibosses", label, "§7killed ${ApexFormatters.timespanText(mark.passedTime())} ago", false, null))
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.reputationTracker || reputation.isEmpty || SkyBlockSession.skyblockIsland != SkyBlockIsland.CRIMSON_ISLE) return
		event.drawHudLines(TConfig.reputationHud, listOf(Component.literal("§cReputation")) + reputation.lines())
	}

	@Subscribe
	fun onWorldChange(event: dev.ashtonite365.apex.events.WorldReadyEvent) {
		lastKill.clear()
	}
}

object RiftHelper {
	@Config
	object TConfig : ManagedConfig("rift-helper", Category.RIFT) {
		val lowTimeAlert by toggle("low-time-alert") { true }
		val lowTimeMinutes by integer("low-time-minutes", 1, 30) { 2 }

		val timeChange by toggle("time-change") { false }
	}

	var timeLeft: kotlin.time.Duration? = null
		private set
	private var alerted = false

	@Subscribe
	fun onTick(event: TickEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.RIFT) { timeLeft = null; alerted = false; return }
		val left = RiftParser.timeLeftFromScoreboard(ScoreboardUtil.simplifiedScoreboardLines) ?: return
		timeLeft = left
		val threshold = TConfig.lowTimeMinutes.minutes
		if (left > threshold) alerted = false
		else if (TConfig.lowTimeAlert && !alerted) {
			alerted = true
			Alerts.show(Component.literal("§5§lRift time: ${ApexFormatters.timespanText(left)} left"))
		}
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {

		val left = timeLeft ?: return
		event.add(Timers.TimerEntry("rift-time", "Rift Time", "§d${ApexFormatters.timespanText(left)}",
			left <= TConfig.lowTimeMinutes.minutes, left))
	}
}

object RiftHighlights {
	@Config
	object TConfig : ManagedConfig("rift-highlights", Category.RIFT) {
		val enabled by riskyToggle("enabled", dev.ashtonite365.apex.gui.config.RiskLevel.RISKY)
		val names by string("names") { "Odonata|Blobbercyst|Leech|Larva|Shy|Bacte" }
	}

	fun matches(name: String, names: String): Boolean =
		names.split('|').map { it.trim() }.any { it.isNotEmpty() && name.contains(it, ignoreCase = true) }

	@Subscribe
	fun onRender(event: dev.ashtonite365.apex.events.WorldRenderLastEvent) {
		if (!TConfig.enabled || SkyBlockSession.skyblockIsland != SkyBlockIsland.RIFT) return
		val level = dev.ashtonite365.apex.util.Client.game.level ?: return
		val names = TConfig.names
		dev.ashtonite365.apex.util.render.WorldDraw.drawInWorld(event) {
			for (entity in level.entitiesForRendering()) {
				val tag = entity.customName?.bareText ?: continue
				if (!matches(tag, names)) continue

				val box = if (entity is net.minecraft.world.entity.decoration.ArmorStand)
					net.minecraft.world.phys.AABB(entity.x - 0.5, entity.y - 2.0, entity.z - 0.5, entity.x + 0.5, entity.y, entity.z + 0.5)
				else entity.boundingBox
				box(box, 0x60AA00FF)
			}
		}
	}
}
