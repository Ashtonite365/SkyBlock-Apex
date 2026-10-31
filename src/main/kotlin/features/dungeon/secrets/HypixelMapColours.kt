// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

object HypixelMapColours {
	const val BLOOD = 18
	const val ENTRANCE = 30
	const val TRAP = 62
	const val NORMAL = 63
	const val PUZZLE = 66
	const val MINIBOSS = 74
	const val FAIRY = 82
	const val UNOPENED = 85
	const val WITHER = 119

	const val WHITE_CHECK = 34

	private val BASE = mapOf(
		4 to 0xFF0000, 7 to 0x007C00, 8 to 0xFFFFFF, 15 to 0xD87F33, 16 to 0xB24CD8, 18 to 0xE5E533, 20 to 0xF27FA5,
		21 to 0x4C4C4C, 29 to 0x191919,
	)
	private val SHADE = intArrayOf(180, 220, 255, 135)

	fun argb(packed: Int): Int? {
		if (packed / 4 == 0) return null
		val base = BASE[packed / 4] ?: return null
		val m = SHADE[packed % 4]
		val r = (base shr 16 and 0xFF) * m / 255; val g = (base shr 8 and 0xFF) * m / 255; val b = (base and 0xFF) * m / 255
		return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
	}

	fun doorKind(packed: Int?): MapLayout.DoorKind? = when (packed) {
		null -> null
		WITHER -> MapLayout.DoorKind.WITHER
		BLOOD -> MapLayout.DoorKind.BLOOD
		ENTRANCE -> MapLayout.DoorKind.ENTRANCE
		else -> MapLayout.DoorKind.NORMAL
	}

	fun doorArgb(kind: MapLayout.DoorKind): Int = argb(when (kind) {
		MapLayout.DoorKind.WITHER -> WITHER
		MapLayout.DoorKind.BLOOD -> BLOOD
		MapLayout.DoorKind.ENTRANCE -> ENTRANCE
		MapLayout.DoorKind.NORMAL -> NORMAL
	})!!

	fun doorColour(samples: List<Int>): Int? {
		val counts = samples.filter { it / 4 != 0 }.groupingBy { it }.eachCount()
		if (counts.isEmpty()) return null
		fun rank(p: Int) = when (p) { WITHER -> 3; BLOOD -> 2; ENTRANCE -> 1; else -> 0 }
		return counts.entries.maxWith(compareBy({ it.value }, { rank(it.key) })).key
	}

	fun isUnentered(roomPacked: Int) = roomPacked == UNOPENED || roomPacked == WHITE_CHECK
}
