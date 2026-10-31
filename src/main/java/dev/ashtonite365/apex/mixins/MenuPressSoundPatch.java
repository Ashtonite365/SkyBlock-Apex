// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.MenuPress;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class MenuPressSoundPatch {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void apex$oneClickPerPress(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (MenuPress.INSTANCE.onLocalSound(sound.getIdentifier(), System.currentTimeMillis())) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } else if (MenuPress.INSTANCE.swallowOtherSound(sound.getIdentifier(), sound.isRelative(), System.currentTimeMillis())) {

            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
