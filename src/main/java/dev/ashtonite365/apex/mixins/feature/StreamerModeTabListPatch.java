// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.streamer.StreamerMode;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public class StreamerModeTabListPatch {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
	private void apex$streamerTabName(PlayerInfo player, CallbackInfoReturnable<Component> result) {
		if (!ModuleGates.streamerMode) return;
		result.setReturnValue(StreamerMode.filterTabName(result.getReturnValue()));
	}
}
