package dev.ashtonite365.apex.mixins.compat.jei;

import dev.ashtonite365.apex.compat.jei.JeiEarlyStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.fabric.startup.ClientLifecycleHandler", remap = false)
public class JeiLifecycleCapturePatch {
	@Inject(require = 0, method = "<init>", at = @At("RETURN"))
	private void apex$capture(CallbackInfo ci) {
		try {
			JeiEarlyStart.capture(this);
		} catch (Throwable ignored) {
		}
	}
}
