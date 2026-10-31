/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: the Gui frame that hosts the screen. Opens the tooltip-deferral frame at HEAD and runs the
 * postponed deferred pass right after the screen (and Fabric's afterExtract overlays), before the saving indicator
 * and toasts; RETURN flushes again as a safety net. Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.util.render.TooltipTopLayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class TooltipTopmostGuiPatch {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("HEAD"))
	private void apex$beginTooltipFrame(DeltaTracker deltaTracker, boolean a, boolean b, CallbackInfo ci) {
		TooltipTopLayer.beginFrame();
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractSavingIndicator(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void apex$flushTooltipOnTop(DeltaTracker deltaTracker, boolean a, boolean b, CallbackInfo ci) {
		TooltipTopLayer.flush();
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("RETURN"))
	private void apex$endTooltipFrame(DeltaTracker deltaTracker, boolean a, boolean b, CallbackInfo ci) {
		TooltipTopLayer.flush();
	}
}
