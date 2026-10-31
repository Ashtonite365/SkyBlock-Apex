/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: while Apex Far Terrain draws beyond the render distance, moves the game's render-distance fog out to
 * the far-terrain edge, so the band at the game's own edge is not faded to the sky colour (a pale ring between near
 * and far terrain). Water, lava, blindness and other environmental fog stay as they are. Leaves everything alone while
 * the feature is off (features/render/farterrain/surface).
 *
 * The push happens right where the game stores its render-distance fog end (the last value it writes before
 * returning), so anything that reads the finished fog at the end of setupFog sees the pushed values whatever order
 * those readers run in. Reading it earlier fogs the game's outermost chunks completely to the sky colour, on top of
 * far terrain: a one-chunk sky band at the edge. The end-of-method hook stays as a fallback; both are idempotent.
 *
 * The game's chunk-count argument of setupFog is part of the handler signature only and is never read: far terrain's
 * fog comes from its own distance (SurfaceFog.fadeStart / fadeEnd).
 */
package dev.ashtonite365.apex.mixins.surfacelod;

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class SurfaceFogMixin {
	@Inject(method = "setupFog", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 0)
	private void apex$farSurfaceFogEarly(Camera camera, int unusedChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir, @Local FogData fog) {
		if (fog != null) SurfaceFog.INSTANCE.adjust(fog, camera.getFluidInCamera() == FogType.ATMOSPHERIC, camera.entity());
	}

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void apex$farSurfaceFog(Camera camera, int unusedChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		FogData fog = cir.getReturnValue();
		if (fog != null) SurfaceFog.INSTANCE.adjust(fog, camera.getFluidInCamera() == FogType.ATMOSPHERIC, camera.entity());
	}
}
