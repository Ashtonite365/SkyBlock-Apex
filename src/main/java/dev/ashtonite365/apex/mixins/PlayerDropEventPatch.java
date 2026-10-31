package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.events.IsSlotProtectedEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class) public abstract class PlayerDropEventPatch {
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	public void apex$guardDrop(LocalPlayer me, boolean stack, CallbackInfo info) {
		var inv = me.getInventory();
		var held = new Slot(inv, inv.getSelectedSlot(), 0, 0);
		boolean blocked = IsSlotProtectedEvent.shouldBlockInteraction(held, ContainerInput.THROW, IsSlotProtectedEvent.MoveOrigin.DROP_FROM_HOTBAR);
		if (blocked) info.cancel();
	}
}
