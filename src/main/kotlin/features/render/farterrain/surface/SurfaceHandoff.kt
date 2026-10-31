/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, which chunks the game can be drawing (pure Kotlin, unit-tested).
 *
 * Far terrain draws every stored chunk the game is not drawing itself. Being loaded is not the same as being drawn:
 * the game never meshes the outermost loaded ring (a chunk needs all its neighbours first) and meshes new chunks a
 * few frames after they load. [neighboursLoaded] is that test; what the game really draws comes from its drawn list
 * (SurfaceDrawnList), and whether the chunk's sections are built is checked per frame by the hand-off itself. Nothing
 * here reads the game's render-distance setting: a radius, where one is used, is measured from the loaded chunks or
 * the drawn list (SurfaceGameArea).
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

object SurfaceHandoff {

	fun roundedDistSq(centerX: Int, centerZ: Int, cx: Int, cz: Int): Long {
		val dx = maxOf(0L, Math.abs(cx - centerX) - 1L)
		val dz = maxOf(0L, Math.abs(cz - centerZ) - 1L)
		return dx * dx + dz * dz
	}

	fun inDisc(centerX: Int, centerZ: Int, radius: Int, cx: Int, cz: Int): Boolean =
		roundedDistSq(centerX, centerZ, cx, cz) < radius.toLong() * radius

	fun neighboursLoaded(cx: Int, cz: Int, loaded: (Int, Int) -> Boolean): Boolean {
		for (dz in -1..1) for (dx in -1..1) if (!loaded(cx + dx, cz + dz)) return false
		return true
	}

	fun fallbackOwned(centerX: Int, centerZ: Int, radius: Int, cx: Int, cz: Int, loaded: (Int, Int) -> Boolean): Boolean =
		inDisc(centerX, centerZ, radius, cx, cz) && neighboursLoaded(cx, cz, loaded)

	fun chunkOf(coord: Double): Int = Math.floor(coord).toInt() shr 4
}
