/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): when Hypixel's resource pack is skipped, items whose item_model
 * component points at a model that only exists in that pack would render as the missing (purple/black)
 * model. Fall back to the vanilla model of the item type instead. Original implementation.
 */
package dev.ashtonite365.apex.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.ashtonite365.apex.features.misc.ServerResourcePackSkip;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public abstract class SkippedPackItemModelFallbackPatch {
	@Shadow
	@Final
	private ModelManager modelManager;

	@ModifyExpressionValue(method = "appendItemLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;", ordinal = 0))
	private Object onGetItemModel(Object original, ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed) {
		return ServerResourcePackSkip.fallbackItemModel(original, stack, modelManager);
	}
}
