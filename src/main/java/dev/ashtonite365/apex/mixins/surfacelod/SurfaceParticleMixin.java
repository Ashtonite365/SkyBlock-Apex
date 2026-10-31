/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: while Apex Far Terrain runs a far block's client animation (features/render/farterrain/surface,
 * SurfaceParticles), its particles go straight to the particle engine (the game's own path drops anything more than
 * 32 blocks from the camera) with the Particles setting applied, and its sounds are not played. Outside that call
 * nothing changes.
 */
package dev.ashtonite365.apex.mixins.surfacelod;

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class SurfaceParticleMixin {
	@Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
	private void apex$farParticle(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z, double xd, double yd, double zd, CallbackInfo ci) {
		if (SurfaceParticles.INSTANCE.getAnimating()) {
			SurfaceParticles.INSTANCE.spawn(particle, alwaysShow, x, y, z, xd, yd, zd);
			ci.cancel();
		}
	}

	@Inject(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V", at = @At("HEAD"), cancellable = true)
	private void apex$farSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay, long seed, CallbackInfo ci) {
		if (SurfaceParticles.INSTANCE.getAnimating()) ci.cancel();
	}
}
