/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex texture engine hook: an equipment layer texture uses the old-style armour texture path
 * (`textures/models/armor/<name>_layer_1|2.png`) when a pack provides one.
 */
package dev.ashtonite365.apex.mixins.texengine;

import dev.ashtonite365.apex.texengine.ApexTextureEngines;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EquipmentClientInfo.Layer.class)
public abstract class ArmorLayerTextureHook {
	@Shadow
	@Final
	private Identifier textureId;

	@Inject(method = "getTextureLocation", at = @At("HEAD"), cancellable = true)
	private void apex$legacyTexturePath(EquipmentClientInfo.LayerType type, CallbackInfoReturnable<Identifier> cir) {
		Identifier legacy = ApexTextureEngines.getActive().getArmor()
			.legacyLayerTexture(textureId, type == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS);
		if (legacy != null) cir.setReturnValue(legacy);
	}
}
