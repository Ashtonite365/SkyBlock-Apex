// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.render.NoFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class NoFogDistancePatch {
	@Inject(method = "setupFog", at = @At("RETURN"), require = 0)
	private void apex$noDistanceFog(Camera camera, int renderDistanceChunks, DeltaTracker deltaTracker, float darkness, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		if (!NoFog.isActive()) return;
		FogData data = cir.getReturnValue();
		if (data != null) NoFog.clearDistance(data, renderDistanceChunks * 16F);
	}
}
