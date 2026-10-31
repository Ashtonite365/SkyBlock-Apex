package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.events.SoundReceiveEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class) public abstract class SoundReceiveEventPatch {
	@WrapWithCondition(method = "handleSoundEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"))
	private boolean apex$playUnlessCancelled(ClientLevel level, @Nullable Entity exclude, double px, double py, double pz, Holder<SoundEvent> what, SoundSource channel, float vol, float pit, long rng) {
		var heard = SoundReceiveEvent.Companion.publish(new SoundReceiveEvent(what, channel, new Vec3(px, py, pz), pit, vol, rng));
		return !heard.getCancelled();
	}
}
