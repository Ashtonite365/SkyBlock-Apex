package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.ashtonite365.apex.events.IsSlotProtectedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public class BlockOffhandSwapLockedSlotPatch {
	@WrapWithCondition(method = "handleKeybinds",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private boolean apex$blockLockedOffhandSwap(ClientPacketListener instance, Packet<?> packet) {
		if (!(packet instanceof ServerboundPlayerActionPacket action)
			|| action.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND)
			return true;
		var player = ((Minecraft) (Object) this).player;
		if (player == null) return true;
		Slot fakeSlot = new Slot(player.getInventory(), player.getInventory().getSelectedSlot(), 0, 0);
		return !IsSlotProtectedEvent.shouldBlockInteraction(fakeSlot, ContainerInput.SWAP, IsSlotProtectedEvent.MoveOrigin.DROP_FROM_HOTBAR);
	}
}
