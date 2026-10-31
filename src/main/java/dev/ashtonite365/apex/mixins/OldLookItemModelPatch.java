/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Classic Item Looks. The model lookup (and the hand swap animation, so held items don't
 * re-equip or flicker) sees the cached classic-look stack instead of the real one. Original implementation.
 * require = 0: a changed target only disables the feature (OldLookItems logs once), never the launch.
 */
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.misc.oldlook.OldLookItems;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public abstract class OldLookItemModelPatch {
	@ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack apex$classicLookLayers(ItemStack stack) {
		return OldLookItems.substitute(stack);
	}

	@ModifyVariable(method = "shouldPlaySwapAnimation", at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack apex$classicLookSwap(ItemStack stack) {
		return OldLookItems.substitute(stack);
	}

	@ModifyVariable(method = "swapAnimationScale", at = @At("HEAD"), argsOnly = true, require = 0)
	private ItemStack apex$classicLookSwapScale(ItemStack stack) {
		return OldLookItems.substitute(stack);
	}
}
