// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.DropdownInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class DropdownInputFirstPatch {
	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void apex$dropdownFirst(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (window != mc.getWindow().handle()) return;
		int button = info.button();
		if (action == 0) { if (DropdownInput.release(button)) ci.cancel(); return; }
		if (action != 1) return;
		MouseHandler self = (MouseHandler) (Object) this;
		if (DropdownInput.press(mc.gui.screen(), self.getScaledXPos(mc.getWindow()), self.getScaledYPos(mc.getWindow()), button)) ci.cancel();
	}
}
