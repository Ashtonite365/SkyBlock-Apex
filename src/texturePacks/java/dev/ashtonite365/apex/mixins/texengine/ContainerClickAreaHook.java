/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: with a layout background, a click counts as outside the container only when it
 * is outside that background (SPEC §2.8).
 */
package dev.ashtonite365.apex.mixins.texengine;

import dev.ashtonite365.apex.texengine.LayoutBackground;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AbstractContainerScreen.class, AbstractRecipeBookScreen.class})
public abstract class ContainerClickAreaHook {
	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void apex$layoutClickArea(double mouseX, double mouseY, int left, int top, CallbackInfoReturnable<Boolean> cir) {
		LayoutBackground bg = ScreenHooks.background();
		if (bg == null) return;
		double x0 = left + bg.getX(), y0 = top + bg.getY();
		boolean inside = mouseX >= x0 && mouseY >= y0 && mouseX < x0 + bg.getWidth() && mouseY < y0 + bg.getHeight();
		cir.setReturnValue(!inside);
	}
}
