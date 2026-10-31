package dev.ashtonite365.apex.mixins.customgui;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.util.customgui.CustomGui;
import dev.ashtonite365.apex.util.customgui.CustomGuiHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class) public abstract class PatchHandledScreen<T extends AbstractContainerMenu> extends PatchGenericScreen {
	@Shadow @Final protected T menu;
	@Shadow protected int leftPos;
	@Shadow protected int topPos;
	@Shadow protected Slot hoveredSlot;
	@Shadow @Final protected int imageWidth;
	@Shadow @Final protected int imageHeight;

	@Unique private boolean apex$slotsMoved;

	@Override
	public void setCustomGui_Apex(@Nullable CustomGui next) {
		super.setCustomGui_Apex(next);
		apex$centre();
	}

	@Unique
	private void apex$centre() {
		CustomGui gui = getCustomGui_Apex();
		int w = imageWidth, h = imageHeight;
		if (gui != null) {
			var first = gui.getBounds().getFirst();
			w = first.width;
			h = first.height;
		}
		leftPos = (width - w) / 2;
		topPos = (height - h) / 2;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void apex$containerInit(CallbackInfo info) {
		apex$centre();
		CustomGui gui = getCustomGui_Apex();
		if (gui != null) gui.onInit();
	}

	@WrapOperation(
		method = "extractSlots",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V"))
	private void apex$slotHooks(AbstractContainerScreen<?> self, GuiGraphicsExtractor g, Slot slot, int mx, int my, Operation<Void> draw) {
		CustomGuiHost.aroundSlot(getCustomGui_Apex(), g, slot, () -> draw.call(self, g, slot, mx, my));
	}

	@WrapOperation(
		method = "extractContents",
		at = {
			@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"),
			@At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
		})
	private void apex$highlightHooks(AbstractContainerScreen<?> self, GuiGraphicsExtractor g, Operation<Void> draw) {
		CustomGuiHost.aroundHighlight(getCustomGui_Apex(), g, hoveredSlot, () -> draw.call(self, g));
	}

	@WrapWithCondition(
		method = "extractContents",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
	private boolean apex$labelsOnlyWithoutGui(AbstractContainerScreen<T> self, GuiGraphicsExtractor g, int mx, int my) {
		CustomGui gui = getCustomGui_Apex();
		return gui == null;
	}

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	public void apex$outside(double x, double y, int left, int top, CallbackInfoReturnable<Boolean> result) {
		CustomGui gui = getCustomGui_Apex();
		if (gui != null) result.setReturnValue(gui.isClickOutsideBounds(x, y));
	}

	@Inject(method = "isHovering(IIIIDD)Z", at = @At("HEAD"), cancellable = true)
	public void apex$hoverRect(int rx, int ry, int rw, int rh, double x, double y, CallbackInfoReturnable<Boolean> result) {
		CustomGui gui = getCustomGui_Apex();
		if (gui != null) result.setReturnValue(gui.isPointWithinBounds(leftPos + rx, topPos + ry, rw, rh, x, y));
	}

	@Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
	public void apex$hoverSlot(Slot target, double x, double y, CallbackInfoReturnable<Boolean> result) {
		CustomGui gui = getCustomGui_Apex();
		if (gui != null) result.setReturnValue(gui.isPointOverSlot(target, leftPos, topPos, x, y));
	}

	@Inject(method = "extractContents", at = @At("HEAD"))
	public void apex$layoutSlots(GuiGraphicsExtractor g, int mx, int my, float partial, CallbackInfo info) {
		apex$slotsMoved = CustomGuiHost.layoutSlots(getCustomGui_Apex(), menu.slots, apex$slotsMoved, leftPos, topPos);
	}
}
