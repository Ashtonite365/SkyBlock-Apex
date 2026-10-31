// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.foraging

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.plus.fishing.PlusFishingProfit
import dev.ashtonite365.apex.features.plus.fishing.PlusInventoryGains
import dev.ashtonite365.apex.features.plus.hunting.PlusHunting
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.repoItemId
import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i

object PlusForagingProfit {
	@Config
	object TConfig : ManagedConfig("foraging-profit", Category.FORAGING) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 160, 80) { Vector2i(5, 160) }
	}

	val tracker = ProfitTracker("Foraging Profit")
	private val inventoryGains = PlusInventoryGains()
	private var axeHeldAt = TimeMark.farPast()
	private val AXE_GRACE = 20.seconds

	fun isAxe(id: String?): Boolean {
		if (id == null || "PICKAXE" in id) return false
		return id.endsWith("_AXE") || id == "TREECAPITATOR_AXE" || "JUNGLE_AXE" in id
	}

	private fun active() = TConfig.enabled && PlusHunting.isHuntingIsland(SkyBlockSession.skyblockIsland)

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = inventoryGains.reset()

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!active()) return
		if (isAxe(Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId)) axeHeldAt = TimeMark.now()
		val gained = inventoryGains.poll()
		if (axeHeldAt.passedTime() > AXE_GRACE) return
		gained.filterKeys { !isAxe(it) }.forEach { (id, amount) -> tracker.addItem(id, amount) }
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!active() || tracker.session.items.isEmpty()) return
		event.drawPlusLines(TConfig.hud, tracker.lines(PlusFishingProfit::nameOf))
	}
}
