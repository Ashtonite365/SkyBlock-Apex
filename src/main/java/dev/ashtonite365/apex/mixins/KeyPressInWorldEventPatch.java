package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ashtonite365.apex.events.WorldKeyboardEvent;
import dev.ashtonite365.apex.keybindings.GenericInputAction;
import dev.ashtonite365.apex.keybindings.InputModifiers;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardHandler.class) public abstract class KeyPressInWorldEventPatch {
	@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
	public boolean apex$worldKey(InputConstants.Key mapped, @Local(argsOnly = true) KeyEvent key) {
		var press = WorldKeyboardEvent.Companion.publish(new WorldKeyboardEvent(GenericInputAction.of(key), InputModifiers.of(key)));
		return !press.getCancelled();
	}
}
