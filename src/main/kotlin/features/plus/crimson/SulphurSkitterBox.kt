// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.crimson

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.islands.CrimsonIsleHelper.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.render.WorldDraw
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

object SulphurSkitterBox {
	private const val REACH = 4
	private const val SEARCH = 15
	private const val COLOUR = 0x30FFFF00
	private var nearestSponge: BlockPos? = null

	fun area(pos: BlockPos): AABB {
		val lo = pos.offset(-REACH, -REACH, -REACH)
		val hi = pos.offset(REACH + 1, REACH + 1, REACH + 1)
		return AABB(lo.x.toDouble(), lo.y.toDouble(), lo.z.toDouble(), hi.x.toDouble(), hi.y.toDouble(), hi.z.toDouble())
	}

	private fun active() = TConfig.sulphurSkitterBox && SkyBlockSession.skyblockIsland == SkyBlockIsland.CRIMSON_ISLE

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!active()) { nearestSponge = null; return }
		if (event.tickCount % 20 != 0) return
		val level = Client.game.level ?: return
		val centre = Client.player?.blockPosition() ?: return
		var best: BlockPos? = null
		var bestDist = 0.0
		for (pos in BlockPos.betweenClosed(centre.offset(-SEARCH, -SEARCH, -SEARCH), centre.offset(SEARCH, SEARCH, SEARCH))) {
			if (!level.getBlockState(pos).`is`(Blocks.SPONGE)) continue
			val dist = pos.distSqr(centre)
			if (best == null || dist < bestDist) { best = pos.immutable(); bestDist = dist }
		}
		nearestSponge = best
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!active()) return
		val sponge = nearestSponge ?: return
		WorldDraw.drawInWorld(event) { box(area(sponge), COLOUR) }
	}
}
