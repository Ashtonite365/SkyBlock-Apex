package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.events.HandledScreenForegroundEvent;
import dev.ashtonite365.apex.hooks.ContainerScreenHooks;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class, priority = 990) public abstract class MixinHandledScreen<T extends AbstractContainerMenu> {
	@Shadow @Final protected T menu;

	@Shadow public abstract T getMenu();

	@Unique Inventory apexInventory;

	@Unique
	private AbstractContainerScreen<?> apex$self() { return (AbstractContainerScreen<?>) (Object) this; }

	@Inject(method = "<init>(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;II)V", at = @At("TAIL"))
	public void apex$keepInventory(AbstractContainerMenu m, Inventory inv, Component name, int w, int h, CallbackInfo info) {
		apexInventory = inv;
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void apex$release(MouseButtonEvent mouse, CallbackInfoReturnable<Boolean> result) {
		if (ContainerScreenHooks.released(apex$self(), mouse)) result.setReturnValue(true);
	}

	@Inject(method = "extractContents", at = @At("HEAD"))
	public void apex$foreground(GuiGraphicsExtractor g, int mx, int my, float partial, CallbackInfo info) {
		if (HandledScreenForegroundEvent.Companion.getHasHandlers())
			HandledScreenForegroundEvent.Companion.publish(new HandledScreenForegroundEvent(apex$self(), g, mx, my, partial));
	}

	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
	public void apex$guardSlotClick(Slot target, int id, int button, ContainerInput input, CallbackInfo info) {
		if (ContainerScreenHooks.dropSlotClick(apex$self(), apexInventory, target, id, button, input)) info.cancel();
	}

	@WrapOperation(method = "extractSlots", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V"))
	public void apex$slotEvents(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot target, int mx, int my, Operation<Void> draw) {
		ContainerScreenHooks.drawSlot(g, target, () -> draw.call(screen, g, target, mx, my));
	}
}
