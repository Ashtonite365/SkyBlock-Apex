// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class) public abstract class StaticMenusInputPatch {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void apex$staticPress(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == 1) StaticMenus.onInput();
	}
}
