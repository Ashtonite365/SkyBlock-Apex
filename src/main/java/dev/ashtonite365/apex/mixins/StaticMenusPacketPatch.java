// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class) public abstract class StaticMenusPacketPatch {
	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void apex$staticContents(ClientboundContainerSetContentPacket packet, CallbackInfo info) {
		StaticMenus.onContents(packet.containerId());
	}

	@Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
	private void apex$staticSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo info) {
		StaticMenus.onSlot(packet.getContainerId());
	}

	@Inject(method = "handleSetCursorItem", at = @At("TAIL"))
	private void apex$staticCursor(ClientboundSetCursorItemPacket packet, CallbackInfo info) {
		StaticMenus.onCursor();
	}

	@Inject(method = "handleOpenScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
	private void apex$staticBeforeOpen(ClientboundOpenScreenPacket packet, CallbackInfo info) {
		StaticMenus.beforeOpenPacket(packet.getContainerId());
	}

	@Inject(method = "handleOpenScreen", at = @At("TAIL"))
	private void apex$staticAfterOpen(ClientboundOpenScreenPacket packet, CallbackInfo info) {
		StaticMenus.afterOpenPacket();
	}

	@Inject(method = "handleContainerClose", at = @At("TAIL"))
	private void apex$staticClose(ClientboundContainerClosePacket packet, CallbackInfo info) {
		StaticMenus.onClose();
	}
}
