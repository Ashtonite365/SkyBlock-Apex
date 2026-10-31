package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.inventory.CursorRestore;
import kotlin.Pair;
import net.minecraft.client.MouseHandler;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class) public abstract class SaveCursorPositionPatch {
	@Shadow private double xpos;
	@Shadow private double ypos;

	@Inject(method = "grabMouse", at = @At(value = "HEAD"))
	public void apex$beforeGrab(CallbackInfo info) { CursorRestore.beforeGrab(xpos, ypos); }

	@Inject(method = "grabMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;", ordinal = 2))
	public void apex$centred(CallbackInfo info) { CursorRestore.afterCentre(xpos, ypos); }

	@Inject(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;", ordinal = 2))
	public void apex$restoreOnRelease(CallbackInfo info) {
		Pair<Double, Double> saved = CursorRestore.restoreOnRelease(xpos, ypos);
		if (saved != null) {
			xpos = saved.getFirst();
			ypos = saved.getSecond();
		}
	}
}
