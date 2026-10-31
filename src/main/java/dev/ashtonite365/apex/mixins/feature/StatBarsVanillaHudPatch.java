// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.features.misc.StatBars;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class StatBarsVanillaHudPatch {
	@Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
	private void apex$hideHearts(CallbackInfo ci) {
		if (!ModuleGates.statBarsVanilla) return;
		if (StatBars.hideVanillaHearts()) ci.cancel();
	}

	@Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
	private void apex$hideFood(GuiGraphicsExtractor graphics, Player player, int y, int x, CallbackInfo ci) {
		if (!ModuleGates.statBarsVanilla) return;
		if (StatBars.hideVanillaHunger()) ci.cancel();
	}

	@Inject(method = "extractArmor", at = @At("HEAD"), cancellable = true)
	private static void apex$hideArmour(GuiGraphicsExtractor graphics, Player player, int a, int b, int c, int d, CallbackInfo ci) {
		if (!ModuleGates.statBarsVanilla) return;
		if (StatBars.hideVanillaArmour()) ci.cancel();
	}

	@WrapWithCondition(method = "extractHotbarAndDecorations",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
	private boolean apex$hideXpLevel(GuiGraphicsExtractor graphics, Font font, int level) {
		if (!ModuleGates.statBarsVanilla) return true;
		return !StatBars.hideVanillaXp();
	}
}
