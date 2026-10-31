package dev.ashtonite365.apex.mixins.input;

import dev.ashtonite365.apex.events.HandledScreenKeyPressedEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class) public abstract class KeyboardHandlerRememberActionPatch {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private static void apex$rememberAction(long window, int glfwAction, KeyEvent key, CallbackInfo info) {
		HandledScreenKeyPressedEvent.Companion.internalPushAction(glfwAction);
	}
}
