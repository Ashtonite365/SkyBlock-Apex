// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.ItemTooltipEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.util.ApexFormatters
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.ScoreboardUtil
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.mc.loreAccordingToNbt
import dev.ashtonite365.apex.util.plus.PlusPatterns
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.plus.matchMatcher
import dev.ashtonite365.apex.util.bareText
import net.minecraft.network.chat.Component
import org.joml.Vector2i

object PlusRiftMotes {
	@Config
	object TConfig : ManagedConfig("rift-motes", Category.RIFT) {
		val stackSellPrice by toggle("stack-sell-price") { false }
		val motesSession by toggle("motes-session") { false }
		val hud by position("hud", 120, 30) { Vector2i(5, 300) }
	}

	private val group = PlusPatterns.group("rift.motes")
	private val sellPrice by group.pattern("lore.sell-price", "(?:Motes )?Sell Price: (?<price>[\\d,.]+) Motes.*")
	private val scoreMotes by group.pattern("scoreboard.motes", "Motes: (?<motes>[\\d,]+).*")
	private val scoreTime by group.pattern("scoreboard.time", "(?:Time left|ф Left): ?(?<time>.+)")
	private val timePart = Regex("(\\d+)([hms])")

	fun parseNumber(s: String) = s.replace(",", "").toDoubleOrNull()

	fun sellPriceOf(lore: List<String>): Double? {
		for (line in lore) return sellPrice.matchMatcher(line.trim()) { parseNumber(group("price")) } ?: continue
		return null
	}

	fun motesOf(lines: List<String>): Long? {
		for (line in lines) return scoreMotes.matchMatcher(line.trim()) { group("motes").replace(",", "").toLongOrNull() } ?: continue
		return null
	}

	fun parseTime(s: String): Int? {
		var seconds: Int? = null
		for (part in timePart.findAll(s)) {
			val n = part.groupValues[1].toInt()
			val unit = when (part.groupValues[2]) {
				"h" -> 3600
				"m" -> 60
				else -> 1
			}
			seconds = (seconds ?: 0) + n * unit
		}
		return seconds
	}

	@Subscribe
	fun onTooltip(event: ItemTooltipEvent) {
		if (!TConfig.stackSellPrice || SkyBlockSession.skyblockIsland != SkyBlockIsland.RIFT) return
		val count = event.stack.count
		if (count < 2) return
		val each = sellPriceOf(event.stack.view().loreAccordingToNbt.map { it.bareText }) ?: return
		event.lines += Component.literal("§7Stack (§f$count§7): §d${ApexFormatters.withThousands(each * count, 0)} Motes")
	}

	private var startMotes: Long? = null
	private var lastMotes: Long? = null
	private var lastTime: Int? = null
	private var lastChange = 0
	private var lastChangeTick = -1000
	private var tick = 0

	@Subscribe
	fun onTick(event: TickEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.RIFT) {
			startMotes = null
			lastTime = null
			return
		}
		val lines = ScoreboardUtil.simplifiedScoreboardLines
		val motes = motesOf(lines)
		if (motes != null) {
			if (startMotes == null) startMotes = motes
			lastMotes = motes
		}
		var time: Int? = null
		for (line in lines) {
			time = scoreTime.matchMatcher(line.trim()) { parseTime(group("time")) }
			if (time != null) break
		}
		if (time != null) {
			val before = lastTime
			if (before != null) {
				val delta = time - before

				if (delta > 1 || delta < -2) {
					lastChange = delta
					lastChangeTick = event.tickCount
				}
			}
			lastTime = time
		}
		tick = event.tickCount
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (SkyBlockSession.skyblockIsland != SkyBlockIsland.RIFT) return
		event.drawPlusLines(TConfig.hud) {
			val start = startMotes
			val now = lastMotes
			if (TConfig.motesSession && start != null && now != null) pair("Motes this session", ApexFormatters.withThousands(now - start))
			val showChange = dev.ashtonite365.apex.features.islands.RiftHelper.TConfig.timeChange && tick - lastChangeTick < 60
			if (showChange) add(if (lastChange > 0) "§a+${lastChange}s Rift time" else "§c${lastChange}s Rift time")
		}
	}
}
