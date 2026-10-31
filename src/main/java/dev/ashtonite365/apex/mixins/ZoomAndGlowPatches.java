/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): zoom key FOV and optional glow (MiscRender). Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.misc.MiscRender;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class ZoomAndGlowPatches {

	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void onCalculateFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(MiscRender.zoomedFov(cir.getReturnValueF()));
	}
}
