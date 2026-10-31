/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: draws Apex Far Terrain inside vanilla's main render pass (features/render/farterrain/surface).
 * Optional (require = 0): if another renderer replaces this code path, SurfaceRenderer.endFrame draws in its own pass.
 */
package dev.ashtonite365.apex.mixins.surfacelod;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class SurfaceLevelRendererMixin {
	private static final String RENDER_GROUP = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/renderpearl/api/textures/GpuSampler;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Z)V";

	@Inject(method = "executeSolid", at = @At(value = "INVOKE", target = RENDER_GROUP, shift = At.Shift.AFTER), require = 0)
	private void apex$farSurfaceOpaque(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, CallbackInfo ci) {
		SurfaceRenderer.INSTANCE.drawOpaqueInPass(pass);
	}

	@Inject(method = "executeClassicTransparency", at = @At(value = "INVOKE", target = RENDER_GROUP), require = 0)
	private void apex$farSurfaceTranslucent(ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, CallbackInfo ci) {
		SurfaceRenderer.INSTANCE.drawTranslucentInPass(pass);
	}
}
