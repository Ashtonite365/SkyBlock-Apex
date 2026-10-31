// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class) public abstract class StaticMenusScreenPatch {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
	private void apex$staticFrameStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo info) {
		if (StaticMenus.frameStart((Screen) (Object) this, graphics, mouseX, mouseY, a)) info.cancel();
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
	private void apex$staticFrameEnd(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo info) {
		StaticMenus.frameEnd();
	}
}
