// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import io.github.moulberry.repo.data.NEUItem
import net.minecraft.core.BlockPos
import dev.ashtonite365.apex.util.SkyBlockIsland

interface NavigableWaypoint {
	val island: SkyBlockIsland
	val position: BlockPos

	val name: String

	data class RepoNpc(val entry: NEUItem) : NavigableWaypoint {
		override val island: SkyBlockIsland get() = SkyBlockIsland.ofMode(entry.island)
		override val position: BlockPos get() = BlockPos(entry.x, entry.y, entry.z)
		override val name: String get() = entry.displayName
	}
}
