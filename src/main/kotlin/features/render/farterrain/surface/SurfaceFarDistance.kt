/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, how far it draws (pure Kotlin, unit-tested).
 *
 * Only far terrain's own settings decide this: "Unlimited Distance" (default on: everything recorded) or "Distance"
 * (chunks, default 128). The game's render distance is never read: changing it changes which chunks the game draws
 * itself (and so which ones far terrain leaves to it), never how far far terrain reaches, its fade or its clip plane.
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

object SurfaceFarDistance {

	fun setting(unlimited: Boolean, distance: Int): Int = if (unlimited) SurfaceRenderer.UNLIMITED else distance

	fun effective(farChunks: Int, farthestRegionDistSq: Long): Int =
		if (farChunks < SurfaceRenderer.UNLIMITED) farChunks
		else (Math.sqrt(farthestRegionDistSq.toDouble()).toInt() + SurfaceRegions.SIZE).coerceAtLeast(16)

	fun farClip(unlimited: Boolean, distance: Int, effectiveFarChunks: Int): Float =
		(if (unlimited) effectiveFarChunks else distance) * 16 * 1.5f + 512f

	fun fadeStart(effectiveFarChunks: Int): Float = effectiveFarChunks * 16f * 0.8f
	fun fadeEnd(effectiveFarChunks: Int): Float = effectiveFarChunks * 16f
}
