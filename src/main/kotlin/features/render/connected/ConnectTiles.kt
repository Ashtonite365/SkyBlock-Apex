/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: connected textures, the neighbour mask and the mask -> tile table. Pure code (no game classes), used by
 * near terrain (through the Fabric renderer API); Apex Far Terrain never connects.
 */
package dev.ashtonite365.apex.features.render.connected

object ConnectTiles {
	const val UP = 1
	const val UP_RIGHT = 2
	const val RIGHT = 4
	const val DOWN_RIGHT = 8
	const val DOWN = 16
	const val DOWN_LEFT = 32
	const val LEFT = 64
	const val UP_LEFT = 128

	@JvmField val SLOT_UP = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
	@JvmField val SLOT_RIGHT = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

	const val K_TOP = 1
	const val K_RIGHT = 2
	const val K_BOTTOM = 4
	const val K_LEFT = 8
	const val K_CORNER_TL = 16
	const val K_CORNER_TR = 32
	const val K_CORNER_BR = 64
	const val K_CORNER_BL = 128

	const val PLANE_ALL = 0xFF

	const val PLANE_ACROSS = RIGHT or LEFT

	const val PLANE_ALONG = UP or DOWN

	fun keyOf(mask: Int): Int {
		var k = 0
		val t = mask and UP != 0
		val r = mask and RIGHT != 0
		val b = mask and DOWN != 0
		val l = mask and LEFT != 0
		if (t) k = k or K_TOP
		if (r) k = k or K_RIGHT
		if (b) k = k or K_BOTTOM
		if (l) k = k or K_LEFT
		if (t && l && mask and UP_LEFT == 0) k = k or K_CORNER_TL
		if (t && r && mask and UP_RIGHT == 0) k = k or K_CORNER_TR
		if (b && r && mask and DOWN_RIGHT == 0) k = k or K_CORNER_BR
		if (b && l && mask and DOWN_LEFT == 0) k = k or K_CORNER_BL
		return k
	}

	@JvmField val KEYS: IntArray = (0 until 256).map { keyOf(it) }.distinct().sorted().toIntArray()

	val TILE_COUNT: Int = KEYS.size

	private val keyToTile = IntArray(256) { -1 }.also { a -> KEYS.forEachIndexed { i, k -> a[k] = i } }

	private val TABLE: ByteArray = ByteArray(256) { keyToTile[keyOf(it)].toByte() }

	@JvmStatic
	fun tileOf(mask: Int): Int = TABLE[mask and 0xFF].toInt()

	fun keyOfTile(tile: Int): Int = KEYS[tile]

	fun reachable(plane: Int): IntArray {
		val seen = BooleanArray(TILE_COUNT)
		for (m in 0 until 256) if (m and plane.inv() == 0) seen[tileOf(m)] = true
		return seen.indices.filter { seen[it] }.toIntArray()
	}
}
