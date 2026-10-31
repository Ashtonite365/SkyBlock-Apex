package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.keybindings.ApexKeyboardState;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class) public abstract class MaintainKeyboardStatePatch {
	@Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/FramerateLimitTracker;onInputReceived()V"))
	private void apex$trackKey(long window, int glfwAction, KeyEvent key, CallbackInfo info) { ApexKeyboardState.INSTANCE.maintainState(key, glfwAction); }
}
