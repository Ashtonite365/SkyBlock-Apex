// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.render.HideWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class HideWeatherRenderPatch {
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
	private void apex$hideWeather(ClientLevel level, float partialTick, Vec3 camera, WeatherRenderState state, CallbackInfo ci) {
		if (!HideWeather.isActive()) return;
		state.reset();
		state.intensity = 0F;
		ci.cancel();
	}
}
