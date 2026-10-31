// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

typealias Cell = Pair<Int, Int>

object MapLayout {
	enum class DoorKind { NORMAL, WITHER, BLOOD, ENTRANCE }

	const val GRAY = 0xFF8A8A8A.toInt()
	val WITHER_DOOR = HypixelMapColours.doorArgb(DoorKind.WITHER)
	val BLOOD_DOOR = HypixelMapColours.doorArgb(DoorKind.BLOOD)
	val ENTRANCE_DOOR = HypixelMapColours.doorArgb(DoorKind.ENTRANCE)

	data class MapFrame(val originX: Int, val originY: Int, val step: Int, val room: Int) {
		fun x(c: Cell) = originX + c.first * step
		fun y(c: Cell) = originY + c.second * step
	}
}
