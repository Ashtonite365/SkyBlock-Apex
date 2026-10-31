// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.misc.CustomScoreboard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class CustomScoreboardPatch {
	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void apex$hideSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		if (!ModuleGates.customScoreboard) return;
		if (CustomScoreboard.shouldHideVanilla()) ci.cancel();
	}
}
