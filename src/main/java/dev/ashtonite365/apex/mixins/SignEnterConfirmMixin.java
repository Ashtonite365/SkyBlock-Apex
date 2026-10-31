// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.apexmarket.SignEnterConfirm;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSignEditScreen.class)
public class SignEnterConfirmMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	public void onKeyPressed_apex(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (SignEnterConfirm.INSTANCE.onKey((AbstractSignEditScreen) (Object) this, event.key())) cir.setReturnValue(true);
	}
}
