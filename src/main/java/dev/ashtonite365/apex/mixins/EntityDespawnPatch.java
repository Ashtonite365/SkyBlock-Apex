package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.events.EntityDespawnEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class) public abstract class EntityDespawnPatch {
	@Inject(method = "removeEntity", at = @At(value = "TAIL"))
	private void apex$gone(int entityId, Entity.RemovalReason why, CallbackInfo info, @Local(name = "entity") @Nullable Entity removed) {
		EntityDespawnEvent.Companion.publish(new EntityDespawnEvent(removed, entityId, why));
	}
}
