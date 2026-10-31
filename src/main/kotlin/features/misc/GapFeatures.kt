/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): remaining gap features: dragon fight tracker, zealot/eye counter,
 * Trevor the Trapper helper, Minos Inquisitor alert (+ risky party share), quiver low warning, custom text HUD,
 * real-time clock, and risky players-in-range. Inspired by,, and feature
 * descriptions only. Original implementation, no code copied. The ⚠ Special Zealot alert/path options of the Dragon
 * Tracker are wired in SpecialZealot.kt (carried End Portal Frame detection: idea).
 */
package dev.ashtonite365.apex.features.misc

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import io.github.notenoughupdates.moulconfig.ChromaColour
import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.monster.Enderman
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.EntityDespawnEvent
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.timers.Timers
import dev.ashtonite365.apex.gui.config.ManagedOption
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.tracker.SessionTracker
import dev.ashtonite365.apex.util.bareText

object DragonTracker {
	@Config
	object TConfig : ManagedConfig("dragon-tracker", Category.COMBAT) {
		val dragons by toggle("dragons") { true }
		val zealots by toggle("zealots") { true }

		val specialZealotAlert by riskyToggle("special-zealot-alert", RiskLevel.RISKY)
		@Suppress("UNCHECKED_CAST")
		val specialZealotSound by riskySubToggle("special-zealot-sound", RiskLevel.RISKY, allOptions.getValue("special-zealot-alert") as ManagedOption<Boolean>) { true }
		@Suppress("UNCHECKED_CAST")
		val specialZealotTitle by riskySubToggle("special-zealot-title", RiskLevel.RISKY, allOptions.getValue("special-zealot-alert") as ManagedOption<Boolean>) { true }
		val specialZealotPath by riskyToggle("special-zealot-path", RiskLevel.RISKY)
		val specialZealotPathColour by colour("special-zealot-path-colour") { ChromaColour.fromStaticRGB(0xFF, 0x55, 0xFF, 0xFF) }
		val hud by position("hud", 150, 60) { Vector2i(5, 400) }
	}

	private val SPAWNED = Regex("^☬ The (\\w+) Dragon has spawned!$")
	private val EYE = Regex("^☬ You placed a Summoning Eye!.*")
	private val DAMAGE = Regex("^\\s*Your Damage: ([0-9,]+).*")
	private val EYE_DROP = Regex("^RARE DROP! Summoning Eye.*")

	fun dragonType(line: String) = SPAWNED.matchEntire(line.trim())?.groupValues?.get(1)
	fun damage(line: String) = DAMAGE.matchEntire(line)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

	val session = SessionTracker(idleTimeout = 15.minutes, resetAfter = 60.minutes)
	var zealotsSinceEye = 0

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.END) return
		val line = event.bareText
		if (TConfig.dragons) {
			dragonType(line)?.let { session.add("$it dragons") }
			if (EYE.matches(line.trim())) session.add("Eyes placed")
			damage(line)?.let { session.add("Damage", it) }
		}
		if (TConfig.zealots && EYE_DROP.matches(line.trim())) {
			session.add("Summoning Eyes"); Client.showChat(Component.literal("§5[SkyBlock Apex] Summoning Eye after $zealotsSinceEye zealots.")); zealotsSinceEye = 0
		}
	}

	@Subscribe
	fun onDespawn(event: EntityDespawnEvent) {
		if (!TConfig.zealots || SkyBlockSession.skyblockIsland != SkyBlockIsland.END) return
		val e = event.entity as? Enderman ?: return
		val p = Client.player ?: return
		if (e.reason() && e.distanceToSqr(p) < 100) { zealotsSinceEye++; session.add("Endermen killed") }
	}

	private fun Enderman.reason() = this.isDeadOrDying

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.END || session.isEmpty) return
		event.drawHudLines(TConfig.hud, listOf(Component.literal("§5The End"), Component.literal("§7Since last eye: §a$zealotsSinceEye")) + session.lines())
	}
}

object TrevorHelper {
	@Config
	object TConfig : ManagedConfig("trevor", Category.GARDEN) {
		val enabled by toggle("enabled") { true }
		val hud by position("hud", 150, 20) { Vector2i(5, 440) }
		val tracker by toggle("tracker") { false }
		val trackerHud by position("tracker-hud", 150, 60) { Vector2i(5, 470) }
	}

	private val TASK = Regex("^\\[NPC] Trevor: You can find your (\\w+) animal near the ([^.]+)\\.?$")
	private val DONE = Regex("^Return to the Trapper soon to get a new animal to hunt!$")
	fun task(line: String) = TASK.matchEntire(line.trim())?.let { it.groupValues[1] to it.groupValues[2] }

	private var current: Pair<String, String>? = null
	private var cooldownUntil: TimeMark? = null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled) return
		val line = event.bareText
		task(line)?.let { current = it; return }
		if (DONE.matches(line.trim())) { current = null; cooldownUntil = TimeMark.now() }
	}

	@Subscribe
	fun onTimers(event: Timers.TimerCollectEvent) {
		val mark = cooldownUntil ?: return
		if (!TConfig.enabled) return
		event.cooldown("trevor", "Trevor", 60.seconds - mark.passedTime())
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		val (rarity, area) = current ?: return
		if (!TConfig.enabled || SkyBlockSession.skyblockIsland != SkyBlockIsland.ofMode("farming_1")) return
		event.drawHudLines(TConfig.hud, listOf(Component.literal("§aTrevor: §f$rarity §7animal near §e$area")))
	}
}

object GapMisc {
	@Config
	object TConfig : ManagedConfig("gap-misc", Category.MISC) {
		val inquisitorAlert by toggle("inquisitor-alert") { true }
		val quiverWarning by toggle("quiver-warning") { true }
		val customText by string("custom-text") { "" }
		val customTextHud by position("custom-text-hud", 120, 30) { Vector2i(5, 470) }
		val clock by toggle("clock") { false }
		val clockHud by position("clock-hud", 60, 10) { Vector2i(5, 500) }
		val playersInRange by riskyToggle("players-in-range", RiskLevel.RISKY)
		val playersRange by integer("players-range", 5, 64) { 30 }
		val playersHud by position("players-hud", 120, 10) { Vector2i(5, 515) }
	}

	private val INQ = Regex("^(?:Uh oh|Oi|Woah|Yikes|Oh)! You dug out a Minos Inquisitor!.*")
	private val QUIVER = Regex("^You only have (\\d+) arrows left in your Quiver!.*")
	fun isInquisitor(line: String) = INQ.matches(line.trim())
	fun quiverLeft(line: String) = QUIVER.matchEntire(line.trim())?.groupValues?.get(1)?.toIntOrNull()

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val line = event.bareText
		if (isInquisitor(line)) {
			if (TConfig.inquisitorAlert) Alerts.show(Component.literal("§d§lINQUISITOR!"), 3.seconds)

		}
		if (TConfig.quiverWarning) quiverLeft(line)?.let { Alerts.show(Component.literal("§c$it arrows left!"), 2.seconds) }
	}

	fun customLines(text: String): List<Component> =
		text.split("\\n", "|").filter { it.isNotBlank() }.map { Component.literal(it.replace('&', '§')) }

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (TConfig.customText.isNotBlank()) event.drawHudLines(TConfig.customTextHud, customLines(TConfig.customText))
		if (TConfig.clock) event.drawHudLines(TConfig.clockHud, listOf(Component.literal(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")))))
		if (TConfig.playersInRange && SkyBlockSession.inSkyBlock) {
			val me = Client.player ?: return
			val r = TConfig.playersRange.toDouble()
			val n = Client.game.level?.players()?.count { it !== me && it.uuid.version() == 4 && it.distanceToSqr(me) <= r * r } ?: 0
			event.drawHudLines(TConfig.playersHud, listOf(Component.literal("§7Players in ${TConfig.playersRange}m: §e$n")))
		}
	}
}
