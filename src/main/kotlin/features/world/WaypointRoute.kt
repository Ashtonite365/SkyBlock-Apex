// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.core.BlockPos

@Serializable
data class WaypointRoute(var label: String, var id: String, var isRelativeTo: String?, var waypoints: MutableList<Waypoint>, var isOrdered: Boolean) {

	@Transient
	var lastRelativeImport: BlockPos? = null

	val size: Int get() = waypoints.size

	fun deepCopy(): WaypointRoute = copy(waypoints = ArrayList(waypoints)).also { it.lastRelativeImport = lastRelativeImport }

	@Serializable
	data class Waypoint(val x: Int, val y: Int, val z: Int) {
		val blockPos: BlockPos get() = BlockPos(x, y, z)

		fun shifted(dx: Int, dy: Int, dz: Int): Waypoint = Waypoint(x + dx, y + dy, z + dz)

		companion object Factory {
			fun from(blockPos: BlockPos): Waypoint = blockPos.run { Waypoint(x, y, z) }
		}
	}
}
