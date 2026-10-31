/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain, fog hand-off between the game's terrain and far terrain.
 *
 * The game fades its own terrain into the sky colour over the last 4..64 blocks of the render distance. With far
 * terrain drawn behind it that fade shows as a pale ring, so while far terrain is on, the render-distance fog is moved
 * out to the far-terrain edge (where far terrain fades itself). Environmental fog (water, lava, blindness, rain) is
 * kept; only clear-weather atmospheric fog, whose default 1024-block end would cut far terrain short, is stretched for
 * far terrain in proportion, so rain still fogs far terrain like near terrain.
 *
 * Far terrain's own fade comes only from its own drawn distance ([fadeStart] / [fadeEnd], SurfaceFarDistance). The
 * game's render-distance fog fields are touched in one place, [adjust]: they are only ever moved further out to that
 * fade (never nearer than the game's own), so the game's terrain is not fogged over in front of far terrain. Nothing
 * of far terrain is decided from them (the source scan test allows exactly this use).
 */
package dev.ashtonite365.apex.features.render.farterrain.surface

import net.minecraft.client.renderer.fog.FogData
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

object SurfaceFog {

	const val DEFAULT_ATMOSPHERIC_END = 1024f

	@Volatile var fadeStart = 0f
	@Volatile var fadeEnd = 0f

	@Volatile var envScale = 1f
		private set

	@Volatile var gameFogEnd = Float.POSITIVE_INFINITY
		private set

	fun adjust(fog: FogData, atmospheric: Boolean, entity: Entity?) {
		gameFogEnd = fogCullEnd(fog.environmentalEnd)
		val end = fadeEnd
		if (end <= 0f) { envScale = 1f; return }
		val blinded = entity is LivingEntity && (entity.hasEffect(MobEffects.BLINDNESS) || entity.hasEffect(MobEffects.DARKNESS))
		fog.renderDistanceStart = pushOut(fog.renderDistanceStart, fadeStart)
		fog.renderDistanceEnd = pushOut(fog.renderDistanceEnd, end)
		envScale = envScaleFor(atmospheric && !blinded, end)
	}

	fun fogCullEnd(environmentalEnd: Float): Float =
		if (environmentalEnd.isNaN() || environmentalEnd <= 0f) Float.POSITIVE_INFINITY else environmentalEnd

	fun pushOut(vanilla: Float, far: Float): Float = maxOf(vanilla, far)

	fun envScaleFor(atmospheric: Boolean, fadeEnd: Float): Float =
		if (!atmospheric) 1f else maxOf(1f, fadeEnd / DEFAULT_ATMOSPHERIC_END)
}
