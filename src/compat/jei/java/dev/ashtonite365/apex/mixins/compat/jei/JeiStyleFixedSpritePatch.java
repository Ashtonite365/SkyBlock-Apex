// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.compat.jei;

import dev.ashtonite365.apex.compat.jei.style.JeiStyleDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "mezz.jei.common.gui.elements.DrawableSprite", remap = false)
public class JeiStyleFixedSpritePatch {
	@Shadow(remap = false) @Final private Supplier<TextureAtlasSprite> spriteSupplier;
	@Shadow(remap = false) @Final private int width;
	@Shadow(remap = false) @Final private int height;

	@Inject(require = 0, method = "draw(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIII)V", at = @At("HEAD"), cancellable = true)
	private void apex$styledFixedSprite(GuiGraphicsExtractor g, int x, int y, int maskTop, int maskBottom, int maskLeft, int maskRight, CallbackInfo ci) {
		if (maskTop != 0 || maskBottom != 0 || maskLeft != 0 || maskRight != 0) return;
		TextureAtlasSprite s;
		try { s = this.spriteSupplier.get(); } catch (Throwable t) { return; }
		if (JeiStyleDraw.drawThemedSprite(g, s, x, y, this.width, this.height)) ci.cancel();
	}
}
