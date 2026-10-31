package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.util.mc.StartupStage;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class) public abstract class MinecraftInitLevelListener {
	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initBackendSystem()Lnet/minecraft/util/TimeSource$NanoTimeSource;"))
	private void apex$backendLevel(CallbackInfo info) { StartupStage.advanceTo(StartupStage.RENDER_INIT); }

	@Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;initRenderer(Lcom/mojang/renderpearl/api/device/GpuDevice;)V"))
	private void apex$rendererLevel(CallbackInfo info) { StartupStage.advanceTo(StartupStage.RENDER); }

	@Inject(method = "<init>", at = @At(value = "TAIL"))
	private void apex$menuLevel(CallbackInfo info) { StartupStage.advanceTo(StartupStage.MAIN_MENU); }
}
