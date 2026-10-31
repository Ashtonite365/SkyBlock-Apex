// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.mixins;

import dev.ashtonite365.apex.features.plus.pets.PetNametag;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class PetNametagPatch<T extends Entity> {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void onGetPetNameTag(T entity, CallbackInfoReturnable<Component> cir) {
		Component replaced = PetNametag.replace(entity, cir.getReturnValue());
		if (replaced != null) cir.setReturnValue(replaced);
	}
}
