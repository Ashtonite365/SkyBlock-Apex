package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.events.EntityUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class) public abstract class EntityUpdateEventListener extends ClientCommonPacketListenerImpl {
	@Shadow private ClientLevel level;

	protected EntityUpdateEventListener(Minecraft mc, Connection link, CommonListenerCookie cookie) { super(mc, link, cookie); }

	@Inject(method = "handleSetEquipment", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
	private void apex$equipment(ClientboundSetEquipmentPacket p, CallbackInfo info, @Local Entity target) {
		if (!(target instanceof LivingEntity living)) return;
		EntityUpdateEvent.Companion.publish(new EntityUpdateEvent.EquipmentUpdate(living, p.getSlots()));
	}

	@Inject(method = "handleUpdateAttributes", at = @At("TAIL"))
	private void apex$attributes(ClientboundUpdateAttributesPacket p, CallbackInfo info) {
		var living = (LivingEntity) level.getEntity(p.getEntityId());
		EntityUpdateEvent.Companion.publish(new EntityUpdateEvent.AttributeUpdate(living, p.getValues()));
	}

	@Inject(method = "handleSetEntityData", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V", shift = At.Shift.AFTER))
	private void apex$trackedData(ClientboundSetEntityDataPacket p, CallbackInfo info, @Local Entity target) {
		var update = new EntityUpdateEvent.TrackedDataUpdate(target, p.packedItems());
		EntityUpdateEvent.Companion.publish(update);
	}
}
