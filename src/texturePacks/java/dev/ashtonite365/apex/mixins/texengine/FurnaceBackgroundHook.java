/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: furnace-type screens draw the layout background instead of their own; the
 * flame and arrow progress sprites still draw on top (SPEC §2.8).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.ashtonite365.apex.texengine.LayoutBackground;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractFurnaceScreen.class)
public abstract class FurnaceBackgroundHook {
	@WrapWithCondition(
		method = "extractBackground",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"),
		allow = 1)
	private boolean apex$background(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		LayoutBackground bg = ScreenHooks.background();
		if (bg == null) return true;
		bg.draw(graphics, (AbstractContainerScreen<?>) (Object) this);
		return false;
	}
}
