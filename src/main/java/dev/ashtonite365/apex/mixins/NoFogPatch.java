// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.render.NoFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.DarknessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.renderer.fog.environment.PowderedSnowFogEnvironment;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AtmosphericFogEnvironment.class, LavaFogEnvironment.class, WaterFogEnvironment.class,
	PowderedSnowFogEnvironment.class, BlindnessFogEnvironment.class, DarknessFogEnvironment.class})
public class NoFogPatch {
	@Inject(method = "setupFog", at = @At("TAIL"), require = 0)
	private void apex$noFog(FogData data, Camera camera, ClientLevel level, float renderDistance, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (!NoFog.isActive()) return;
		NoFog.clear(data, renderDistance, Minecraft.getInstance().options.cloudRange().get() * 16F);
	}
}
