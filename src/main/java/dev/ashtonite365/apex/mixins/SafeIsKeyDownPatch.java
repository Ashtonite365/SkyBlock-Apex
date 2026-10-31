package dev.ashtonite365.apex.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ashtonite365.apex.keybindings.SanitizeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public class SafeIsKeyDownPatch {
	@Inject(method = "isKeyDown(I)Z", at = @At("HEAD"), cancellable = true)
	private static void apex$boundsCheck(int key, CallbackInfoReturnable<Boolean> cir) {
		if (!SanitizeInput.isValidKeyboardKey(key)) cir.setReturnValue(false);
	}
}
