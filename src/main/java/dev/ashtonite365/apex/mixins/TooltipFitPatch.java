/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: every tooltip stays on screen (flip/clamp, and scale down a tooltip taller than the screen).
 * Maths in TooltipFit. Hooks the single tooltip draw all setTooltipForNextFrame variants end in. Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.inventory.TooltipFit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipFitPatch {
	@Unique private float apex$tooltipScale = 1f;
	@Unique private int apex$tooltipDepth = 0;

	@ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true)
	private ClientTooltipPositioner apex$fitTooltip(ClientTooltipPositioner original) {
		return (sw, sh, mx, my, tw, th) -> {
			float s = TooltipFit.INSTANCE.scale(tw, th, sw, sh);
			var p = TooltipFit.INSTANCE.position((int) (mx / s), (int) (my / s), tw, th, (int) (sw / s), (int) (sh / s));
			return new Vector2i(p.getFirst(), p.getSecond());
		};
	}

	@Inject(method = "tooltip", at = @At("HEAD"), cancellable = true)
	private void apex$pushScale(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner pos, Identifier style, boolean focused, CallbackInfo ci) {

		if (lines.isEmpty()) { ci.cancel(); return; }
		var self = (GuiGraphicsExtractor) (Object) this;
		int w = 0, h = lines.size() == 1 ? -2 : 0;
		for (var c : lines) { w = Math.max(w, c.getWidth(font)); h += c.getHeight(font); }
		h += 2;
		apex$tooltipScale = TooltipFit.INSTANCE.scale(w, h, self.guiWidth(), self.guiHeight());
		self.pose().pushMatrix();
		if (apex$tooltipScale < 1f) self.pose().scale(apex$tooltipScale, apex$tooltipScale);
		apex$tooltipDepth++;
	}

	@Inject(method = "tooltip", at = @At("RETURN"))
	private void apex$popScale(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner pos, Identifier style, boolean focused, CallbackInfo ci) {
		if (apex$tooltipDepth <= 0) return;
		apex$tooltipDepth--;
		((GuiGraphicsExtractor) (Object) this).pose().popMatrix();
		apex$tooltipScale = 1f;
	}
}
