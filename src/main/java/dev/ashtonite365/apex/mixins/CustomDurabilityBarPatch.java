package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.ashtonite365.apex.util.DurabilityBarEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class) public abstract class CustomDurabilityBarPatch {

	@Unique private static final int FULL_WIDTH = 13;

	@WrapOperation(
		method = "itemBar",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isBarVisible()Z")
	)
	private boolean apex$visible(ItemStack stack, Operation<Boolean> vanilla, @Share("barOverride") LocalRef<DurabilityBarEvent.DurabilityBar> shared) {
		if (vanilla.call(stack)) return true;
		var bar = DurabilityBarEvent.Companion.publish(new DurabilityBarEvent(stack)).getBarOverride();
		shared.set(bar);
		return bar != null;
	}

	@WrapOperation(method = "itemBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getBarWidth()I"))
	private int apex$width(ItemStack stack, Operation<Integer> vanilla, @Share("barOverride") LocalRef<DurabilityBarEvent.DurabilityBar> shared) {
		var bar = shared.get();
		return bar == null ? vanilla.call(stack) : Math.round(bar.getPercentage() * FULL_WIDTH);
	}

	@WrapOperation(method = "itemBar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getBarColor()I"))
	private int apex$colour(ItemStack stack, Operation<Integer> vanilla, @Share("barOverride") LocalRef<DurabilityBarEvent.DurabilityBar> shared) {
		var bar = shared.get();
		return bar == null ? vanilla.call(stack) : bar.getColor().getColor();
	}
}
