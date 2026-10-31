// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.menus.garden

object CropCards {
	const val COLS = 7

	fun cells(tiles: List<GardenTile>, controls: List<GardenTile>): List<GardenTile?> {
		val crops = tiles.sortedBy { it.slot }
		val padded: List<GardenTile?> = crops + List((COLS - crops.size % COLS) % COLS) { null }
		return padded + controls
	}

	private val harvestIntro = Regex("^Harvest .* tier!$")

	fun body(lore: List<String>): List<String> {
		val lines = lore.map { it.trim() }

		val end = lines.indexOfFirst { it.endsWith("tier!") }
		val intro = end >= 0 && harvestIntro.matches(lines.take(end + 1).joinToString(" "))
		return (if (intro) lines.drop(end + 1) else lines)
			.map { it.replace(" Experience", " XP") }
			.dropWhile { it.isEmpty() }
	}
}
