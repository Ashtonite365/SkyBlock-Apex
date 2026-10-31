// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.menus.StaticMenus;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class) public abstract class StaticMenusSlotPatch {
	@Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
	private void apex$staticItem(CallbackInfoReturnable<ItemStack> result) {
		if (StaticMenus.drawing == null) return;
		ItemStack shown = StaticMenus.shownItem((Slot) (Object) this);
		if (shown != null) result.setReturnValue(shown);
	}
}
