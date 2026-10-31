// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.hunting

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.features.plus.fishing.PlusFishingProfit
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.plus.ProfitTracker
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.plus.findMatcher
import dev.ashtonite365.apex.util.bareText
import org.joml.Vector2i

object PlusHuntingProfit {
	@Config
	object TConfig : ManagedConfig("hunting-profit", Category.FORAGING) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 160, 80) { Vector2i(5, 200) }
	}

	val tracker = ProfitTracker("Hunting Profit")
	private val shardLine by PlusPatterns.group("hunting.profit").pattern("chat.shard",
		"(?:You caught|LOOT SHARE You received|You received|NAGA|SALT) (?:an? |x?(?<amount>\\d+)x? )?(?<name>[A-Za-z' -]+?) Shards?!?.*")
	private val SEPARATORS = Regex("[' -]+")

	fun parseShard(msg: String): Pair<String, Long>? = shardLine.findMatcher(msg) {
		val amount = group("amount")?.toLongOrNull() ?: 1L
		val id = "SHARD_" + SEPARATORS.replace(group("name").trim().uppercase(), "_")
		id to amount
	}

	private fun active() = TConfig.enabled && SkyBlockSession.inSkyBlock

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!active()) return
		parseShard(event.bareText.trim())?.let { (id, amount) -> tracker.addItem(id, amount) }
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!active() || tracker.session.items.isEmpty()) return
		event.drawPlusLines(TConfig.hud, tracker.lines(PlusFishingProfit::nameOf))
	}
}
