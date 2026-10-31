/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): resolve the tooltip style before vanilla turns it into
 * "<ns>:tooltip/<path>_background/_frame" sprites. Picks the per-rarity style and falls back to the default
 * style when a style's sprites are missing from the GUI atlas (no purple-black tooltips). Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.inventory.RarityTooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Optional;

@Mixin(GuiGraphicsExtractor.class)
public class TooltipStylePatch {
	@ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
		at = @At("HEAD"), argsOnly = true)
	private Identifier apex$rarityStyle(Identifier style, Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
		return RarityTooltips.resolveForLines(style, lines);
	}

	@ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V",
		at = @At("HEAD"), argsOnly = true)
	private Identifier apex$rarityStyle2(Identifier style, Font font, List<Component> lines) {
		return RarityTooltips.resolveForLines(style, lines);
	}

	@ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;IIZLnet/minecraft/resources/Identifier;)V",
		at = @At("HEAD"), argsOnly = true)
	private Identifier apex$fallbackStyle(Identifier style) {
		return RarityTooltips.resolveForLines(style, null);
	}
}
