// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.misc.CompactTabList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class CompactTabListPatch {
	@Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
	private void apex$compactTabList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!ModuleGates.compactTabList) return;
		if (!CompactTabList.isActive()) return;
		ci.cancel();
		if (Minecraft.getInstance().options.keyPlayerList.isDown())
			CompactTabList.render(graphics);
	}
}
