/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: pushes the far clip plane out to Apex Far Terrain's distance while it is on
 * (features/render/farterrain/surface). Leaves vanilla's value alone while the feature is off.
 */
package dev.ashtonite365.apex.mixins.surfacelod;

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class SurfaceCameraDepthFarMixin {
	@Shadow
	private float depthFar;

	@Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 0)
	private void apex$farSurfaceDepth(DeltaTracker deltaTracker, CallbackInfo ci) {
		this.depthFar = SurfaceRenderer.depthFar(this.depthFar);
	}
}
