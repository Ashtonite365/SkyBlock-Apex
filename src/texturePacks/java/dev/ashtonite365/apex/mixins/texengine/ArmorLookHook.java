/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: when the armour layer reads a stack's EQUIPPABLE component to draw a worn piece,
 * the engine's armour look for that SkyBlock item is used instead (SPEC §2.6).
 */
package dev.ashtonite365.apex.mixins.texengine;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorLookHook {
	@WrapOperation(
		method = "renderArmorPiece",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private Object apex$armorLook(ItemStack stack, DataComponentType<?> type, Operation<Object> original, @Local(argsOnly = true) EquipmentSlot slot) {
		if (type == DataComponents.EQUIPPABLE) {
			Equippable look = ApexTextureEngines.getActive().getArmor().equippableFor(stack, slot);
			if (look != null) return look;
		}
		return original.call(stack, type);
	}
}
