package dev.ashtonite365.apex.mixins.render.entitytints;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.ashtonite365.apex.events.EntityRenderTintEvent;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderSetup.class) public abstract class ReplaceOverlayTexture {
	@ModifyVariable(method = "prepareTextures", at = @At("LOAD"), name = "overlayTexture")
	private static GpuTextureView apex$overlay(GpuTextureView vanilla) {
		var override = EntityRenderTintEvent.overlayOverride;
		return override == null ? vanilla : override.getTextureView();
	}
}
