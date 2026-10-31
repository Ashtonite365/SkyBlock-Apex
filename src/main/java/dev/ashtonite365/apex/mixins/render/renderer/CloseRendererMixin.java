package dev.ashtonite365.apex.mixins.render.renderer;

import dev.ashtonite365.apex.util.render.ApexRenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class) public abstract class CloseRendererMixin {
	@Inject(method = "close", at = @At("TAIL"))
	private void apex$releaseOurs(CallbackInfo info) { ApexRenderSystem.shutdown(); }
}
