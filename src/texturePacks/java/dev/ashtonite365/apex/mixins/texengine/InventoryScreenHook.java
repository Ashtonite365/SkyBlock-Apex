/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: the player inventory screen's background and its title (SPEC §2.8, §2.9).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.ashtonite365.apex.texengine.LayoutBackground;
import dev.ashtonite365.apex.texengine.ScreenHooks;
import dev.ashtonite365.apex.texengine.TitleMover;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenHook {
	@WrapWithCondition(
		method = "extractBackground",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private boolean apex$background(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		LayoutBackground bg = ScreenHooks.background();
		if (bg == null) return true;
		bg.draw(graphics, (AbstractContainerScreen<?>) (Object) this);
		return false;
	}

	@WrapOperation(
		method = "extractLabels",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
		allow = 1)
	private void apex$title(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow, Operation<Void> original) {
		TitleMover mover = ScreenHooks.containerTitle();
		original.call(graphics, font, mover.text(text), mover.x(font, text, x), mover.y(y), mover.color(text, color), shadow);
	}
}
