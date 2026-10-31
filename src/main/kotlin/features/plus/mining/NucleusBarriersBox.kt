// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.mining

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.mining.MiningExtras.TConfig
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.render.WorldDraw
import net.minecraft.world.phys.AABB

object NucleusBarriersBox {
	private const val WIDTH = 12.0
	private const val HEIGHT = 14.0

	enum class Crystal(val box: AABB, val colour: Int) {
		AMBER(box(474, 111, 524), 0x40FFAA00),
		AMETHYST(box(474, 111, 492), 0x40AA00AA),
		TOPAZ(box(508, 111, 473), 0x40FFFF55),
		JADE(box(542, 111, 492), 0x4055FF55),
		SAPPHIRE(box(542, 111, 524), 0x405555FF),
	}

	fun box(x: Int, y: Int, z: Int): AABB {
		val min = doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble())
		return AABB(min[0], min[1], min[2], min[0] + WIDTH, min[1] + HEIGHT, min[2] + WIDTH)
	}

	private val NUCLEUS = AABB(463.0, 63.0, 460.0, 563.0, 190.0, 563.0)

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.nucleusBarriers || SkyBlockSession.skyblockIsland != SkyBlockIsland.CRYSTAL_HOLLOWS) return
		val position = Client.player?.position() ?: return
		if (!NUCLEUS.contains(position)) return
		WorldDraw.drawInWorld(event) {
			Crystal.entries.forEach { box(it.box, it.colour) }
		}
	}
}
