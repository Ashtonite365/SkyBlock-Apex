package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ashtonite365.apex.events.SlotClickEvent;
import dev.ashtonite365.apex.features.menus.MenuPress;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class) public abstract class SlotClickEventPatch {
	@Unique
	private static boolean apex$inMenu(AbstractContainerMenu m, int index) { return index >= 0 && index < m.slots.size(); }

	@Inject(method = "handleContainerInput", at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;slots:Lnet/minecraft/core/NonNullList;", opcode = Opcodes.GETFIELD))
	private void apex$copyBefore(int window, int index, int button, ContainerInput input, Player who, CallbackInfo info, @Local AbstractContainerMenu m, @Share("slotContent") LocalRef<ItemStack> before) {
		if (apex$inMenu(m, index)) before.set(m.getSlot(index).getItem().copy());
	}

	@Inject(method = "handleContainerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private void apex$countForPress(int window, int index, int button, ContainerInput input, Player who, CallbackInfo info) {
		MenuPress.INSTANCE.clickSent(System.currentTimeMillis());
	}

	@Inject(method = "handleContainerInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
	private void apex$publishClick(int window, int index, int button, ContainerInput input, Player who, CallbackInfo info, @Local AbstractContainerMenu m, @Share("slotContent") LocalRef<ItemStack> before) {
		if (!apex$inMenu(m, index)) return;
		SlotClickEvent.Companion.publish(new SlotClickEvent(m.getSlot(index), before.get(), button, input));
	}
}
