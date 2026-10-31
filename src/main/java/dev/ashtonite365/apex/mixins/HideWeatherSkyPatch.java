// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.render.HideWeather;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.attribute.WeatherAttributes$WeatherAccess$1")
public class HideWeatherSkyPatch {
	@Shadow @Final Level val$level;

	@Inject(method = {"rainLevel", "thunderLevel"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void apex$clearSky(CallbackInfoReturnable<Float> cir) {
		if (val$level != null && val$level.isClientSide())
			cir.setReturnValue(HideWeather.visibleLevel(HideWeather.isActive(), cir.getReturnValue()));
	}
}
