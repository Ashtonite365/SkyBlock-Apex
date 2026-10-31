// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks

@OptIn(NeedsComponents::class)
object GardenLanes {

	data class Lane(val alongX: Boolean, val min: Double, val max: Double)

	fun laneFrom(ax: Double, az: Double, bx: Double, bz: Double): Lane {
		val alongX = abs(ax - bx) >= abs(az - bz)
		val lo = if (alongX) minOf(ax, bx) else minOf(az, bz)
		val hi = if (alongX) maxOf(ax, bx) else maxOf(az, bz)
		return Lane(alongX, lo, hi)
	}

	fun distanceToEnd(lane: Lane, pos: Double, velocity: Double): Double? {
		val inside = lane.min <= pos && pos <= lane.max
		if (!inside || velocity == 0.0) return null
		return if (velocity > 0.0) lane.max - pos else pos - lane.min
	}

	private var lane: Lane? = null
	private var firstEnd: Pair<Double, Double>? = null
	private var warned = false
	var hudLine: Component? = null
		private set

	private fun active() = TConfig.farmingLanes && SkyBlockSession.skyblockIsland == SkyBlockIsland.GARDEN

	@Subscribe
	fun onKey(event: WorldKeyboardEvent) {
		if (!active() || !event.matches(TConfig.laneMarkKey)) return
		val player = Client.player ?: return
		val start = firstEnd
		if (start != null) {
			lane = laneFrom(start.first, start.second, player.x, player.z)
			firstEnd = null
			Client.showChat(Component.literal("§aFarming lane set."))
			return
		}
		firstEnd = player.x to player.z
		Client.showChat(Component.literal("§eLane start set. Walk to the other end and press again."))
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		hudLine = null
		val current = lane ?: return
		if (!active()) return
		val player = Client.player ?: return
		val pos: Double
		val speed: Double
		if (current.alongX) { pos = player.x; speed = (player.x - player.xo) * 20 } else { pos = player.z; speed = (player.z - player.zo) * 20 }
		val dist = distanceToEnd(current, pos, if (abs(speed) < 0.05) 0.0 else speed)
		if (dist == null) {
			warned = false
			return
		}
		val secs = dist / abs(speed)
		hudLine = Component.literal("§7Lane end in: §e%.1f §7blocks (§b%.1fs§7)".format(dist, secs))
		val warnAt = TConfig.laneWarnSeconds
		if (warnAt > 0 && secs <= warnAt && !warned) {
			warned = true
			Alerts.show(Component.literal("§eLane switch!"), 1.seconds)
		} else if (secs > warnAt) {
			warned = false
		}
	}

	private val cropBlocks by lazy {
		setOf(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.NETHER_WART, Blocks.PUMPKIN, Blocks.MELON,
			Blocks.COCOA, Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM, Blocks.SUNFLOWER)
	}

	fun isNoBreakItem(id: String?): Boolean {
		if (id == null) return false
		return id == "FISHING_ROD" || id.endsWith("_ROD") || id.startsWith("ROD_") || id.startsWith("SPRAYONATOR")
	}

	@Subscribe
	fun onAttack(event: AttackBlockEvent) {
		if (!TConfig.noBreak || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN) return
		if (event.player !== Client.player) return
		if (event.world.getBlockState(event.blockPos).block !in cropBlocks) return
		if (isNoBreakItem(event.player.mainHandItem.view().repoItemId?.skyblockId)) event.cancel()
	}
}
