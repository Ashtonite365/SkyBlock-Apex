/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): optional toast hider (see MiscExtras). Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.misc.MiscExtras;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class HideToastsPatch {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void onAddToast(Toast toast, CallbackInfo ci) {
		if (MiscExtras.shouldHideToast(toast)) ci.cancel();
	}
}
