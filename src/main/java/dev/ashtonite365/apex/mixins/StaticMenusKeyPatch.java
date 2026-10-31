// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class) public abstract class StaticMenusKeyPatch {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void apex$staticKey(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action == 1) StaticMenus.onInput();
	}
}
