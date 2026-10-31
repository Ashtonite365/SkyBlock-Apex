// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.menus;

import dev.ashtonite365.apex.features.menus.style.Frost;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class FrostCapturePatch {
	@Inject(method = "render()V", at = @At("HEAD"))
	private void apex$captureFrost(CallbackInfo ci) {
		Frost.capture();
	}
}
