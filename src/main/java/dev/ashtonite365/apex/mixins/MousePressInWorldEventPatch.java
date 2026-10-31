package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ashtonite365.apex.events.WorldKeyboardEvent;
import dev.ashtonite365.apex.keybindings.GenericInputAction;
import dev.ashtonite365.apex.keybindings.InputModifiers;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class) public abstract class MousePressInWorldEventPatch {
	@WrapWithCondition(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
	public boolean apex$worldButton(InputConstants.Key mapped, @Local(argsOnly = true) MouseButtonInfo raw) {
		var press = new WorldKeyboardEvent(GenericInputAction.of(raw), InputModifiers.of(raw));
		return !WorldKeyboardEvent.Companion.publish(press).getCancelled();
	}
}
