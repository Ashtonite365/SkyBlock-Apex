// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.render.HideWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class HideWeatherLevelPatch {
	@Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void apex$noWeatherTick(CallbackInfo ci) {
		if (HideWeather.isActive()) ci.cancel();
	}

	@Inject(method = "setSkyFlashTime", at = @At("HEAD"), cancellable = true, require = 0)
	private void apex$noSkyFlash(int time, CallbackInfo ci) {
		if (HideWeather.isActive()) ci.cancel();
	}
}
