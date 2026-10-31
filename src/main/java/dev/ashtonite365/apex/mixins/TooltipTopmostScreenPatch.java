/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: postpone the screen's deferred tooltip pass until every after-screen overlay has drawn
 * (see TooltipTopLayer). Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.util.render.TooltipTopLayer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
public abstract class TooltipTopmostScreenPatch {
	@WrapWithCondition(method = "extractRenderStateWithTooltipAndSubtitles",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"))
	private boolean apex$deferTooltipToTop(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		return !TooltipTopLayer.defer(graphics, mouseX, mouseY, a);
	}
}
