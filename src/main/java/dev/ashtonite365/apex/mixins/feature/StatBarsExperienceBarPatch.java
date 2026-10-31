// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.misc.StatBars;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBar.class)
public class StatBarsExperienceBarPatch {
	@Inject(method = {"extractBackground", "extractRenderState"}, at = @At("HEAD"), cancellable = true)
	private void apex$hideXpBar(CallbackInfo ci) {
		if (!ModuleGates.statBarsVanilla) return;
		if (StatBars.hideVanillaXp()) ci.cancel();
	}
}
