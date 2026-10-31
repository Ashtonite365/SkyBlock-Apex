// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.render.Fullbright;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public class FullbrightLightmapPatch {
	@Unique
	private static final Vector3fc APEX$WHITE = new Vector3f(1F, 1F, 1F);

	@Inject(method = "render", at = @At("HEAD"), require = 0)
	private void apex$fullbright(LightmapRenderState state, CallbackInfo ci) {
		if (!Fullbright.isEnabled()) return;
		state.needsUpdate = true;
		state.ambientColor = APEX$WHITE;
		state.darknessEffectScale = 0F;
		state.bossOverlayWorldDarkening = 0F;
	}
}
