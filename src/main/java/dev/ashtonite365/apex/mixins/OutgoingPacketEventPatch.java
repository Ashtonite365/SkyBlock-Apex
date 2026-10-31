package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.OutgoingPacketEvent;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class) public abstract class OutgoingPacketEventPatch {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	public void apex$beforeSend(Packet<?> outgoing, CallbackInfo info) {
		var sent = OutgoingPacketEvent.Companion.publish(new OutgoingPacketEvent(outgoing));
		if (sent.getCancelled()) info.cancel();
	}
}
