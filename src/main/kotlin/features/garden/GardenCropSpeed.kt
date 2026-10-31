// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.garden

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.AttackBlockEvent
import dev.ashtonite365.apex.features.garden.GardenProgress.Crop
import dev.ashtonite365.apex.features.garden.GardenTools.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.repoItemId
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.Attributes

@OptIn(NeedsComponents::class)
object GardenCropSpeed {

	val optimalSpeeds: Map<Crop, Int> = buildMap {
		for (c in listOf(Crop.WHEAT, Crop.CARROT, Crop.POTATO, Crop.NETHER_WART)) put(c, 93)
		for (c in listOf(Crop.PUMPKIN, Crop.MELON, Crop.COCOA_BEANS)) put(c, 155)
		put(Crop.SUGAR_CANE, 328)
		put(Crop.CACTUS, 464)
		put(Crop.MUSHROOM, 233)
	}

	private const val WINDOW_MS = 5_000L
	private val recentBreaks = ArrayDeque<Long>()

	fun bps(times: Collection<Long>, now: Long, windowMs: Long = WINDOW_MS): Double {
		var inWindow = 0
		for (t in times) if (now - t <= windowMs) inWindow++
		return inWindow * 1000.0 / windowMs
	}

	@Subscribe
	fun onBreak(event: AttackBlockEvent) {
		if (!TConfig.bpsTracker || SkyBlockSession.skyblockIsland != SkyBlockIsland.GARDEN || event.player !== Client.player) return
		val now = System.currentTimeMillis()
		recentBreaks.addLast(now)
		while (recentBreaks.isNotEmpty() && now - recentBreaks.first() > WINDOW_MS) recentBreaks.removeFirst()
	}

	fun currentBps(): Double = bps(recentBreaks, System.currentTimeMillis())

	private fun walkSpeed(): Int? = Client.player?.getAttributeValue(Attributes.MOVEMENT_SPEED)?.let { (it * 1000).toInt() }

	fun hudLines(): List<Component> = buildList {
		if (TConfig.bpsTracker) {
			val rate = currentBps()
			if (rate > 0) add(Component.literal("§eBlocks/s: §f%.1f".format(rate)))
		}
		if (TConfig.optimalSpeed) {
			val crop = Client.player?.mainHandItem?.view()?.repoItemId?.skyblockId?.let(Crop::forTool)
			val target = crop?.let(optimalSpeeds::get) ?: return@buildList
			val speed = walkSpeed()
			val colour = if (speed == target) "§a" else "§c"
			val note = if (speed != null && speed != target) " §7(current $speed)" else ""
			add(Component.literal("§eOptimal speed: $colour$target$note"))
		}
	}
}
