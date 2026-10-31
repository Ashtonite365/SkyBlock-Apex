/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: when the item model resolver reads a stack's ITEM_MODEL component, the engine's
 * model wins if it exists; otherwise the stack's own model goes through ItemModelFallback (SPEC §1).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import dev.ashtonite365.apex.texengine.ItemModelCatalog;
import dev.ashtonite365.apex.texengine.ItemModelIndex;
import dev.ashtonite365.apex.util.mc.ItemModelFallback;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelLookupHook {
	@WrapOperation(
		method = "appendItemLayers",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object apex$chooseItemModel(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
		if (type != DataComponents.ITEM_MODEL) return original.call(stack, type);
		ItemModelCatalog drawable = ItemModelIndex.INSTANCE;
		Identifier engineModel = ApexTextureEngines.getActive().getItems().overrideFor(stack, drawable);
		if (engineModel != null && drawable.hasItemModel_Apex(engineModel)) return engineModel;
		Object own = original.call(stack, type);
		if (own instanceof Identifier ownId) {
			return ItemModelFallback.INSTANCE.choose(ownId, stack.getItem().components().get(DataComponents.ITEM_MODEL), drawable::hasItemModel_Apex);
		}
		return own;
	}
}
