/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): per-category volume boost up to 300% (MiscExtras). Original implementation.
 * 1) SoundEngine.calculateVolume clamps each sound to 1.0; the boost multiplies its result.
 * 2) OpenAL clamps source gain to AL_MAX_GAIN (default 1.0); Channel.setVolume raises it to match and
 *    sanitises the gain so no NaN/negative/oversized value reaches OpenAL.
 */
package dev.ashtonite365.apex.mixins;

import com.mojang.blaze3d.audio.Channel;
import dev.ashtonite365.apex.features.misc.MiscExtras;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Channel.class)
public class VolumeBoostChannelPatch {
	@Shadow @Final private int source;

	@ModifyVariable(method = "setVolume", at = @At("HEAD"), argsOnly = true)
	private float onSetVolume(float volume) {
		float safe = MiscExtras.safeGain(volume);
		AL10.alSourcef(this.source, AL10.AL_MAX_GAIN, Math.max(1f, safe));
		return safe;
	}
}
