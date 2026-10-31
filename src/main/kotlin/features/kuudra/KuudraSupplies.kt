
package dev.ashtonite365.apex.features.kuudra

import kotlin.time.Duration.Companion.milliseconds
import org.joml.Vector2i
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.drawHudLines
import net.minecraft.network.chat.Component

object KuudraSupplies {
	val identifier: String
		get() = "kuudra-supplies"

	@Config
	object TConfig : ManagedConfig(category = Category.KUUDRA, name = identifier) {
		val enabled by toggle("enabled") { true }
		val hud by position("hud", 150, 80) { Vector2i(4, 160) }
		val sendSupplyTime by toggle("send-supply-time") { false }
		val supplyWaypoints by riskyToggle("supply-waypoints", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val dropWaypoints by riskyToggle("drop-waypoints", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val renderArea by riskyToggle("render-area", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val pearlWaypoints by riskyToggle("pearl-waypoints", dev.ashtonite365.apex.gui.config.RiskLevel.GREY_AREA)
		val pearlClosestOnly by toggle("pearl-closest-only") { true }
	}

	private val supplyRegex = "(?:\\[[^]]+] )?(\\w+) recovered one of Elle's supplies! \\((\\d)/6\\)".toRegex()

	val inSupplyPhase get() = runStart != null && supplyPhaseDone == null
	internal var runStart: Long? = null
	private val pickups = mutableListOf<Triple<String, Int, Long>>()
	private var supplyPhaseDone: Long? = null

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		val t = event.bareText.trim()
		val now = System.currentTimeMillis()
		if (t.startsWith("[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!")) {
			runStart = now; pickups.clear(); supplyPhaseDone = null; return
		}
		val m = supplyRegex.matchEntire(t) ?: return
		val n = m.groupValues[2].toInt()
		pickups.add(Triple(m.groupValues[1], n, now))
		if (n == 6) supplyPhaseDone = now
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || SkyBlockSession.skyblockIsland != SkyBlockIsland.ofMode("kuudra")) return
		val start = runStart ?: return
		val count = pickups.maxOfOrNull { it.second } ?: 0
		val lines = mutableListOf<Component>(Component.literal("§eSupplies: §f$count/6"))
		pickups.takeLast(6).forEach { (name, n, at) ->
			lines.add(Component.literal("§7$n. §f$name §7${ApexFormatters.timespanText((at - start).milliseconds)}"))
		}
		event.drawHudLines(TConfig.hud, lines)
	}
}
