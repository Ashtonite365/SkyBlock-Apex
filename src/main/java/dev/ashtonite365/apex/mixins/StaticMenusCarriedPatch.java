// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class) public abstract class StaticMenusCarriedPatch {
	@Inject(method = "getCarried", at = @At("HEAD"), cancellable = true)
	private void apex$staticCarried(CallbackInfoReturnable<ItemStack> result) {
		if (StaticMenus.drawing == null) return;
		ItemStack shown = StaticMenus.shownCarried((AbstractContainerMenu) (Object) this);
		if (shown != null) result.setReturnValue(shown);
	}
}
