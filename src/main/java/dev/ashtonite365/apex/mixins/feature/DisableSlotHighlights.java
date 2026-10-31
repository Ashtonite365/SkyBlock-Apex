package dev.ashtonite365.apex.mixins.feature;

import dev.ashtonite365.apex.features.fixes.Fixes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import dev.ashtonite365.apex.modules.ModuleGates;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class) public abstract class DisableSlotHighlights {
	@Shadow public abstract ItemStack getItem();

	@Inject(method = "isHighlightable", at = @At("HEAD"), cancellable = true)
	private void apex$noHighlight(CallbackInfoReturnable<Boolean> result) {
		if (!ModuleGates.fixes) return;
		if (Fixes.TConfig.INSTANCE.getNoSlotHighlight() && apex$filler()) result.setReturnValue(false);
	}

	@Unique
	private boolean apex$filler() {
		var tooltip = getItem().get(DataComponents.TOOLTIP_DISPLAY);
		return tooltip != null && tooltip.hideTooltip();
	}
}
