// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.features.menus.MenuPress;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public class MenuPressScreenPatch {
    @WrapOperation(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean apex$onePressOneSound(Screen screen, MouseButtonEvent event, boolean doubled, Operation<Boolean> original) {
        if (!MenuPress.INSTANCE.isApexScreen(screen.getClass().getName())) return original.call(screen, event, doubled);
        return MenuPress.INSTANCE.press(() -> original.call(screen, event, doubled));
    }
}
