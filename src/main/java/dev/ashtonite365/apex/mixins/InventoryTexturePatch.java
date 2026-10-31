/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: the player inventory background follows the selected menu style (Skyglass / Forge) when custom
 * menus are on; vanilla's texture otherwise. The dark-grey "Crafting" label follows the style too, so it stays
 * readable on the Skyglass glass (unchanged with custom menus off). Choice in InventoryTexture. Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.style.InventoryTexture;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InventoryScreen.class)
public abstract class InventoryTexturePatch {
	@ModifyArg(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"),
		index = 1)
	private Identifier apex$styledInventory(Identifier original) {
		return InventoryTexture.pick(original);
	}

	@ModifyArg(method = "extractLabels", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
		index = 4)
	private int apex$styledLabelColour(int original) {
		return InventoryTexture.labelColour(original);
	}
}
