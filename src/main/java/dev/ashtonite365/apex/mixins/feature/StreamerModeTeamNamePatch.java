// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.streamer.StreamerMode;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTeam.class)
public class StreamerModeTeamNamePatch {
	@Inject(method = "formatNameForTeam", at = @At("RETURN"), cancellable = true)
	private static void apex$streamerTeamName(CallbackInfoReturnable<MutableComponent> result) {
		if (!ModuleGates.streamerMode) return;
		result.setReturnValue(StreamerMode.filterTeamName(result.getReturnValue()));
	}
}
