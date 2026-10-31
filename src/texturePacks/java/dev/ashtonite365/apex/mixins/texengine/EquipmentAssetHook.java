/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: equipment assets the engine made from pack `layers` are served by the engine;
 * every other asset id goes to vanilla.
 */
package dev.ashtonite365.apex.mixins.texengine;

import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquipmentAssetManager.class)
public abstract class EquipmentAssetHook {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void apex$engineAsset(ResourceKey<EquipmentAsset> key, CallbackInfoReturnable<EquipmentClientInfo> cir) {
		EquipmentClientInfo made = ApexTextureEngines.getActive().getArmor().clientInfoFor(key.identifier());
		if (made != null) cir.setReturnValue(made);
	}
}
