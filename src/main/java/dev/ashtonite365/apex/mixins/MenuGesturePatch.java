// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.menus.MenuInputGuard;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MenuGesturePatch {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void apex$pressStartsClean(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1) MenuInputGuard.INSTANCE.rawPress();
    }

    @Inject(method = "onButton", at = @At("RETURN"))
    private void apex$releaseEndsGesture(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 0) MenuInputGuard.INSTANCE.end();
    }

    @WrapOperation(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
    private boolean apex$gestureRelease(Screen screen, MouseButtonEvent event, Operation<Boolean> original) {
        if (MenuInputGuard.INSTANCE.consumeRelease(screen)) return true;
        return original.call(screen, event);
    }

    @WrapOperation(method = "handleAccumulatedMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private boolean apex$gestureDrag(Screen screen, MouseButtonEvent event, double dx, double dy, Operation<Boolean> original) {
        if (MenuInputGuard.INSTANCE.consumeDrag()) return true;
        return original.call(screen, event, dx, dy);
    }
}
