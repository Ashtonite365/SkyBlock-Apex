// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.items.ItemCustomizer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemCustomizationPatch {
	@Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
	private void onGetHoverName(CallbackInfoReturnable<Component> cir) {
		Component name = ItemCustomizer.INSTANCE.customName((ItemStack) (Object) this);
		if (name != null) cir.setReturnValue(name);
	}

	@Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
	private void onHasFoil(CallbackInfoReturnable<Boolean> cir) {
		Boolean glint = ItemCustomizer.INSTANCE.customGlint((ItemStack) (Object) this);
		if (glint != null) cir.setReturnValue(glint);
	}
}
