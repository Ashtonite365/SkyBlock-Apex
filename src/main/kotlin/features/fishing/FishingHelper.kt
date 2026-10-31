// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.fishing

import org.joml.Vector2i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import kotlin.time.Duration.Companion.seconds
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import dev.ashtonite365.apex.util.bareText

object FishingHelper {
	@Config
	object TConfig : ManagedConfig("fishing-helper", Category.FISHING) {
		val biteAlert by toggle("bite-alert") { true }
		val biteSound by toggle("bite-sound") { true }
		val castTimer by toggle("cast-timer") { true }
		val hookTimer by toggle("hook-timer") { true }
		val hud by position("hud", 100, 20) { Vector2i(0, 40) }
		val baitDisplay by toggle("bait-display") { true }
		val noBaitWarning by toggle("no-bait-warning") { true }
		val totemOverlay by toggle("totem-overlay") { true }
		val totemExpiryAlert by integer("totem-expiry-alert", 0, 60) { 10 }
		val nearbyCreatures by toggle("nearby-creatures") { true }

		val nearbyCreaturesHealth by toggle("nearby-creatures-health") { false }
		val nearbyCreaturesLimit by integer("nearby-creatures-limit", 1, 10) { 5 }
		val nearbyCreaturesRareFirst by toggle("nearby-creatures-rare-first") { true }
		val nearbyCreaturesSeparate by toggle("nearby-creatures-separate") { false }
		val nearbyCreaturesHud by position("nearby-creatures-hud", 140, 50) { Vector2i(5, 120) }
		val creatureCapAlert by integer("creature-cap-alert", 0, 60) { 0 }

		val creatureTimeAlert by integer("creature-time-alert", 0, 600) { 0 }
		val baitChangeWarning by toggle("bait-change-warning") { false }
		val killTimeMessage by toggle("kill-time-message") { false }
		val hideOtherBobbers by toggle("hide-other-bobbers") { false }

		val muteBanshee by toggle("mute-banshee") { false }
		val hideChumBuckets by toggle("hide-chum-buckets") { false }
		val hideOwnChumBucket by toggle("hide-own-chum-bucket") { false }
		val bobberCount by riskyToggle("bobber-count", RiskLevel.GREY_AREA)
		val hotspotWaypoint by riskyToggle("hotspot-waypoint", RiskLevel.GREY_AREA)
		val highlightRareCreatures by riskyToggle("highlight-rare-creatures", RiskLevel.GREY_AREA)
	}

	private var castTime: TimeMark? = null
	var biting = false
		private set
	private var lastHookId = -1
	private var hookCountdown: String? = null
	private val HOOK_TIMER = Regex("(\\d+(?:\\.\\d+)?)")

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!SkyBlockSession.inSkyBlock) return reset()
		val hook = Client.player?.fishing ?: return reset()
		if (hook.id != lastHookId) {
			lastHookId = hook.id
			castTime = TimeMark.now()
			biting = false
		}
		val wasBiting = biting
		val tags = hook.level().getEntitiesOfClass(ArmorStand::class.java, hook.boundingBox.inflate(1.5, 3.0, 1.5)) {
			it.customName != null
		}.mapNotNull { it.customName?.bareText?.trim() }
		biting = tags.any { it == "!!!" }

		hookCountdown = tags.firstNotNullOfOrNull { HOOK_TIMER.matchEntire(it)?.groupValues?.get(1) }
		if (biting && !wasBiting && TConfig.biteAlert) Alerts.show(Component.literal("§c§l!!! Fish biting !!!"), 1.seconds, sound = TConfig.biteSound)
		else if (biting && !wasBiting && TConfig.biteSound) Alerts.sound()
	}

	private fun reset() {
		castTime = null
		hookCountdown = null
		biting = false
		lastHookId = -1
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!SkyBlockSession.inSkyBlock) return
		val timers = mutableListOf<Component>()
		val cast = castTime
		if (TConfig.castTimer && cast != null)
			timers.add(Component.literal("§bCast: §f" + ApexFormatters.timespanText(cast.passedTime())))
		if (TConfig.hookTimer) {
			if (biting) timers.add(Component.literal("§c§l!!!"))
			else hookCountdown?.let { timers.add(Component.literal("§eHook: §f${it}s")) }
		}

		val lines = dev.ashtonite365.apex.features.timers.TimerBoards.keep("cast-hook", timers).toMutableList()
		lines.addAll(FishingExtras.hudLines())
		val health = FishingExtras.creatureHealthLines()
		if (TConfig.nearbyCreaturesSeparate) {
			if (health.isNotEmpty()) event.drawHudLines(TConfig.nearbyCreaturesHud, health)
		} else lines.addAll(health)
		event.drawHudLines(TConfig.hud, lines)
	}
}
