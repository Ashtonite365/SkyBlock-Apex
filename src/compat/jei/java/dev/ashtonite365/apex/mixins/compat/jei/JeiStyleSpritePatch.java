// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.compat.jei;

import dev.ashtonite365.apex.compat.jei.style.JeiStyleDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.common.gui.elements.ScalableDrawable", remap = false)
public class JeiStyleSpritePatch {
	@Shadow(remap = false) @Final private Identifier spriteId;

	@Inject(require = 0, method = "draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
	private void apex$styledSprite(GuiGraphicsExtractor g, int x, int y, int w, int h, CallbackInfo ci) {
		if (JeiStyleDraw.drawThemed(g, this.spriteId, x, y, w, h)) ci.cancel();
	}
}
