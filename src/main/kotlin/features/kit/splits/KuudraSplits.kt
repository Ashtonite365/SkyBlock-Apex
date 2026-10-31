// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.splits

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.features.dungeon.DungeonSplits
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.kit.ServerTicks
import dev.ashtonite365.apex.util.render.drawHudLines
import kotlin.time.Duration.Companion.milliseconds
import org.joml.Vector2i

object KuudraSplits {
	@Config
	object TConfig : ManagedConfig("kuudra-splits", Category.KUUDRA) {
		val enabled by toggle("enabled") { false }
		val hud by position("hud", 130, 70) { Vector2i(4, 290) }
		val tickTime by toggle("tick-time") { false }
		val hidePending by toggle("hide-pending") { false }
		val fixedWidth by toggle("fixed-width") { false }
	}

	private var current = SplitRun(KitSplitDefs.kuudra)

	private fun inKuudra() = SkyBlockSession.skyblockIsland == SkyBlockIsland.KUUDRA

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		current = SplitRun(KitSplitDefs.kuudra)
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!inKuudra()) return
		current.onMessage(event.bareText.trim(), System.currentTimeMillis(), ServerTicks.totalTicks)
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!TConfig.enabled || !inKuudra()) return
		val splits = current.rows(System.currentTimeMillis(), ServerTicks.totalTicks)
		if (splits.isEmpty()) return
		val shown = ArrayList<Pair<String, String>>()
		for (split in splits) {
			val pending = split.time == 0L
			if (pending && TConfig.hidePending) continue
			val value = buildString {
				if (pending) {
					append("§7--")
				} else {
					append("§a").append(ApexFormatters.timespanText(split.time.milliseconds))
					if (TConfig.tickTime) append(" §8(§7").append(SplitRun.formatTicks(split.ticks)).append("§8)")
				}
			}
			shown.add("§e${split.name}:" to value)
		}
		event.drawHudLines(TConfig.hud, DungeonSplits.layoutRows(shown, TConfig.fixedWidth))
	}
}
