// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.TimeMark
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.repoItemId
import kotlin.time.Duration.Companion.seconds
import org.joml.Vector2i

object PlusFishingProfit {
	@Config
	object TConfig : ManagedConfig("fishing-profit", Category.FISHING) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 160, 80) { Vector2i(5, 120) }
	}

	val tracker = ProfitTracker("Fishing Profit")
	private val inventoryGains = PlusInventoryGains()
	private var rodHeldAt = TimeMark.farPast()
	private val ROD_GRACE = 30.seconds
	private val COLOUR_CODE = dev.ashtonite365.apex.util.SharedRegex.SECTION

	fun isRod(id: String?): Boolean {
		if (id == null) return false
		return id.endsWith("_ROD") || "FISHING_ROD" in id || id == "ROD_OF_THE_SEA" || id.endsWith("_LINE_ROD")
	}

	private fun active() = TConfig.enabled && SkyBlockSession.inSkyBlock

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = inventoryGains.reset()

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!active()) return
		if (isRod(Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId)) rodHeldAt = TimeMark.now()
		val gained = inventoryGains.poll()
		if (rodHeldAt.passedTime() > ROD_GRACE) return
		gained.filterKeys { !isRod(it) }.forEach { (id, amount) -> tracker.addItem(id, amount) }
	}

	fun nameOf(id: String): String {
		val display = RepoManager.getCoreItem(ItemId(id))?.displayName ?: return id
		return COLOUR_CODE.replace(display, "")
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!active() || tracker.session.items.isEmpty()) return
		event.drawPlusLines(TConfig.hud, tracker.lines(::nameOf))
	}
}
