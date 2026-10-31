// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.features.menus.MenuPress;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public class MenuEchoEntitySoundPatch {
    @WrapWithCondition(method = "handleSoundEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"))
    private boolean apex$muteMenuEcho(ClientLevel level, Entity except, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed) {
        return !MenuPress.INSTANCE.swallowServerSoundNow(sound.value().location());
    }
}
