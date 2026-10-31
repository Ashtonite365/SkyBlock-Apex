/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: block event / item pickup / entity status packets as WorldActionPacketEvent (Secrets: opened
 * chests). Read-only; fires after the thread hop, before the client handles the packet (the picked-up item still
 * exists, so its position is readable).
 */
package dev.ashtonite365.apex.mixins.feature;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.ashtonite365.apex.events.WorldActionPacketEvent;

@Mixin(ClientPacketListener.class)
public abstract class WorldActionPacketsPatch {
	@Shadow
	private ClientLevel level;

	@Inject(method = "handleBlockEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void onBlockEventApex(ClientboundBlockEventPacket packet, CallbackInfo ci) {
		if (!WorldActionPacketEvent.Companion.getHasHandlers()) return;
		WorldActionPacketEvent.Companion.publish(new WorldActionPacketEvent.BlockAction(packet.getPos(), packet.getBlock(), packet.getB0(), packet.getB1()));
	}

	@Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void onTakeItemApex(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		if (!WorldActionPacketEvent.Companion.getHasHandlers()) return;
		if (level == null) return;
		var item = level.getEntity(packet.getItemId());
		if (item != null)
			WorldActionPacketEvent.Companion.publish(new WorldActionPacketEvent.ItemTaken(item, packet.getPlayerId(), packet.getAmount()));
	}

	@Inject(method = "handleEntityEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void onEntityEventApex(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		if (!WorldActionPacketEvent.Companion.getHasHandlers()) return;
		if (level == null) return;
		var entity = packet.getEntity(level);
		if (entity != null)
			WorldActionPacketEvent.Companion.publish(new WorldActionPacketEvent.EntityStatus(entity, packet.getEventId()));
	}
}
