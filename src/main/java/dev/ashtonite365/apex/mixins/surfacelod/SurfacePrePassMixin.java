/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: prepares Apex Far Terrain's frame (uploads, buffer growth, indirect commands, uniforms) just before
 * vanilla opens its main render pass (features/render/farterrain/surface/SurfaceFrameSchedule.kt): the game refuses
 * buffer writes while a pass is open. Optional (require = 0): without it the frame is prepared after the main pass
 * (END_MAIN) and drawn in far terrain's own pass.
 */
package dev.ashtonite365.apex.mixins.surfacelod;

import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceManager;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class SurfacePrePassMixin {

	@Inject(method = "lambda$addMainPass$0", at = @At("HEAD"), require = 0)
	private void apex$farSurfaceBeforeMainPass(CallbackInfo ci) {
		SurfaceManager.beforeMainPass();
	}
}
