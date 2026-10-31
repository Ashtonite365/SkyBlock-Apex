package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.ChestInventoryUpdateEvent;
import dev.ashtonite365.apex.events.PlayerInventoryUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class) public abstract class SlotUpdateListener extends ClientCommonPacketListenerImpl {
	@Unique private static final int PLAYER_WINDOW = 0;

	protected SlotUpdateListener(Minecraft mc, Connection link, CommonListenerCookie cookie) { super(mc, link, cookie); }

	@Unique
	private int apex$window(int id) {
		var me = minecraft.player;
		assert me != null;
		if (id == PLAYER_WINDOW) return 1;
		return id == me.containerMenu.containerId ? 2 : 0;
	}

	@Inject(method = "handleContainerSetSlot", at = @At(value = "TAIL"))
	private void apex$oneSlot(ClientboundContainerSetSlotPacket p, CallbackInfo info) {
		switch (apex$window(p.getContainerId())) {
			case 1 -> PlayerInventoryUpdate.Companion.publish(new PlayerInventoryUpdate.Single(p.getSlot(), p.getItem()));
			case 2 -> ChestInventoryUpdateEvent.Companion.publish(new ChestInventoryUpdateEvent.Single(p.getItem(), p.getSlot()));
			default -> { }
		}
	}

	@Inject(method = "handleContainerContent", at = @At("TAIL"))
	private void apex$allSlots(ClientboundContainerSetContentPacket p, CallbackInfo info) {
		switch (apex$window(p.containerId())) {
			case 1 -> PlayerInventoryUpdate.Companion.publish(new PlayerInventoryUpdate.Multi(p.items()));
			case 2 -> ChestInventoryUpdateEvent.Companion.publish(new ChestInventoryUpdateEvent.Multi(p.items()));
			default -> { }
		}
	}
}
