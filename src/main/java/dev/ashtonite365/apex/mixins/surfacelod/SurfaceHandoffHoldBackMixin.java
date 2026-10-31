/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Apex Far Terrain hand-off (features/render/farterrain/surface/SurfaceHandoffTiming.kt).
 * While far terrain still draws a chunk (the 1 s wait after the game fully built it, and its fade-in), the game's own
 * sections for that chunk are left out of its draw list, so the two copies never overlap and the game's copy appears
 * only once it is complete. Optional (require = 0): with a replacement terrain renderer this step does not run and
 * both copies stay drawn (far terrain behind, through its depth bias).
 */
package dev.ashtonite365.apex.mixins.surfacelod;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.render.farterrain.surface.SurfaceManager;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class SurfaceHandoffHoldBackMixin {

	@Inject(method = "extractSectionDrawGroups", at = @At("HEAD"), require = 0)
	private void apex$handoffBeforeDrawList(boolean respectTranslucentOrder, List<?> sectionInfos, Map<?, ?> drawGroups, CallbackInfoReturnable<Integer> cir) {
		SurfaceManager.beforeSectionDrawList();
	}

	@WrapOperation(method = "extractSectionDrawGroups", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;getRenderSectionSlice(Lnet/minecraft/client/renderer/chunk/SectionMesh;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSectionBufferSlice;"),
		require = 0)
	private SectionRenderDispatcher.RenderSectionBufferSlice apex$holdBack(SectionRenderDispatcher dispatcher, SectionMesh mesh, ChunkSectionLayer layer,
		Operation<SectionRenderDispatcher.RenderSectionBufferSlice> original) {
		if (SurfaceManager.holdBack(mesh)) return null;
		return original.call(dispatcher, mesh, layer);
	}
}
