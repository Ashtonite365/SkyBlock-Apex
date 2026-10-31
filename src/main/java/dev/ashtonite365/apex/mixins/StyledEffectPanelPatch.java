/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (atoll-1031): the status effect panels next to the inventory use the selected menu style's frame and
 * text colours (InventoryWidgetStyle) when custom menus are on; vanilla's sprite and colours otherwise. Original.
 */
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.ashtonite365.apex.features.menus.style.InventoryWidgetStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EffectsInInventory.class)
public abstract class StyledEffectPanelPatch {
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void apex$styledEffectBackground(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!InventoryWidgetStyle.draw(g, x, y, w, h, false)) original.call(g, pipeline, sprite, x, y, w, h);
	}

	@ModifyArg(method = "extractText", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)V"),
		index = 4)
	private int apex$styledEffectName(int original) {
		return InventoryWidgetStyle.textColour(original);
	}

	@ModifyArg(method = "extractText", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
		index = 4)
	private int apex$styledEffectDuration(int original) {
		return InventoryWidgetStyle.textColour(original);
	}
}
