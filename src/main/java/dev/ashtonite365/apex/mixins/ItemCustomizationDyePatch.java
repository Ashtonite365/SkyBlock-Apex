// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.items.ItemCustomizer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DyedItemColor.class)
public class ItemCustomizationDyePatch {
	@Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true)
	private static void onGetColor(ItemStack stack, int defaultColor, CallbackInfoReturnable<Integer> cir) {
		Integer colour = ItemCustomizer.INSTANCE.customColour(stack);
		if (colour != null) cir.setReturnValue(colour);
	}
}
